package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.Dtos.PayPerson;
import com.grove.Domain.Dtos.PayPersonInput;
import com.grove.Domain.Dtos.PersonBumpInput;
import com.grove.Domain.Dtos.PayMatrixEntry;
import com.grove.Domain.Dtos.PayRules;
import com.grove.Domain.Dtos.PayRulesUpload;
import com.grove.Domain.PayCode;
import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.PayCodeRepository;
import com.grove.Repository.PayMatrixRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.WeeklyStatRepository;

/**
 * Weekly hourly pay:
 * <ul>
 * <li>sales = after-discount service sales + 20% of the discount amount</li>
 * <li>PPH = sales ÷ floor hours</li>
 * <li>everyone has a base rate: the stylist minimum ($16 to start), or the receptionist
 *     rate ($13 to start) for receptionists, unless the owner sets one just for them</li>
 * <li>stylists get the matrix rate for the highest step at or below PPH, or their base rate,
 *     whichever is higher</li>
 * <li>leads and managers (by their role on the site) get the owner's bump for that role on top:
 *     $0.50 for leads and $1.00 for managers to start</li>
 * <li>receptionists get their base rate, never the matrix</li>
 * </ul>
 * The matrix comes from the payroll skill's matrix.json, loaded by the owner; it and the
 * base rates are kept only in the database (the code repository is public). Personal base
 * rates live in the old pay_codes table's flat_rate column; code and premium are unused.
 */
@Service
public class PayService {

	static final BigDecimal DEFAULT_MINIMUM = new BigDecimal("16.00");
	static final String MINIMUM_KEY = "pay.minimumRate";
	static final BigDecimal DEFAULT_RECEPTIONIST_RATE = new BigDecimal("13.00");
	static final String RECEPTIONIST_KEY = "pay.receptionistRate";
	/** "pay.removed.ava stone" = the date the owner took Ava off the pay list. */
	static final String REMOVED_PREFIX = "pay.removed.";
	static final BigDecimal DISCOUNT_SHARE = new BigDecimal("0.2");
	/** The kinds of bump the owner sets amounts for, and where each amount is kept. */
	static final Map<String, String> BUMP_KEYS = Map.of(
		"LEAD", "pay.leadBump", "LEAD_PROD", "pay.leadProdBump", "MANAGER", "pay.managerBump");
	static final Map<String, BigDecimal> DEFAULT_BUMPS = Map.of(
		"LEAD", new BigDecimal("0.50"), "LEAD_PROD", new BigDecimal("0.75"), "MANAGER", new BigDecimal("1.00"));
	/** What someone can be given on the pay table besides following their role. */
	static final java.util.Set<String> PERSON_BUMPS = java.util.Set.of("NONE", "LEAD", "LEAD_PROD", "MANAGER", "CUSTOM");
	/** Retail commission, as in the skill: 10% of retail when retail is at least $60 or product per guest at least $1.50. */
	static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");
	static final BigDecimal MIN_RETAIL_FOR_COMMISSION = new BigDecimal("60");
	static final BigDecimal MIN_PPG_FOR_COMMISSION = new BigDecimal("1.50");

	private final PayMatrixRepository matrix;
	private final PayCodeRepository codes;
	private final AppSettingRepository settings;
	private final UserRepository users;
	private final WeeklyStatRepository stats;

	public PayService(PayMatrixRepository matrix, PayCodeRepository codes, AppSettingRepository settings, UserRepository users,
			WeeklyStatRepository stats) {
		this.matrix = matrix;
		this.codes = codes;
		this.settings = settings;
		this.users = users;
		this.stats = stats;
	}

	/** (net + (gross − net) × 0.2) ÷ floor hours, to the cent for showing; 0 with no floor hours. */
	public static BigDecimal pph(WeeklyStat s) {
		return exactPph(s).setScale(2, RoundingMode.HALF_UP);
	}

	/** PPH unrounded, for the matrix lookup: payroll compares the exact figure (38.996 is still under 39). */
	static BigDecimal exactPph(WeeklyStat s) {
		BigDecimal hours = orZero(s.getFloorHours());
		if (hours.signum() <= 0) return BigDecimal.ZERO;
		BigDecimal net = orZero(s.getNetServiceSales());
		BigDecimal gross = orZero(s.getServiceSales());
		BigDecimal sales = net.add(gross.subtract(net).multiply(DISCOUNT_SHARE));
		return sales.divide(hours, 10, RoundingMode.HALF_UP);
	}

	/** Pay rates for many weeks of one person: a lookup that reads the tables once. */
	public Function<WeeklyStat, BigDecimal> rates(String suName) {
		List<PayMatrixRow> steps = sortedMatrix();
		if (steps.isEmpty()) return s -> null;
		Role role = roleOf(suName);
		PayCode row = personalRow(suName);
		BigDecimal base = baseRate(suName, role);
		BigDecimal bump = personBump(row, role);
		if (role == Role.RECEPTIONIST) return s -> base.add(bump).setScale(2, RoundingMode.HALF_UP);
		return s -> rate(exactPph(s), steps, base, bump);
	}

	public static BigDecimal commission(WeeklyStat s) {
		BigDecimal retail = orZero(s.getRetailSales());
		BigDecimal ppg = orZero(s.getProductPerGuest());
		boolean eligible = retail.compareTo(MIN_RETAIL_FOR_COMMISSION) >= 0 || ppg.compareTo(MIN_PPG_FOR_COMMISSION) >= 0;
		return eligible ? retail.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
	}

	/** True once the owner has loaded the pay matrix. */
	public boolean ratesLoaded() {
		return matrix.count() > 0;
	}

	/** The base rate the owner set just for this person, or null if they use their role's default. */
	public BigDecimal personalRate(String suName) {
		PayCode row = personalRow(suName);
		return row == null ? null : row.getFlatRate();
	}

	/** The default base rate for a role: the receptionist rate for receptionists, the stylist minimum for everyone else. */
	public BigDecimal defaultRate(Role role) {
		return role == Role.RECEPTIONIST ? money(RECEPTIONIST_KEY, DEFAULT_RECEPTIONIST_RATE) : minimum();
	}

	/** Their base rate: what the owner set for them, or their role's default. */
	public BigDecimal baseRate(String suName, Role role) {
		BigDecimal own = personalRate(suName);
		return (own != null ? own : defaultRate(role)).setScale(2, RoundingMode.HALF_UP);
	}

	/** Their row of things the owner set just for them (base rate, bump), or null. */
	private PayCode personalRow(String suName) {
		return suName == null ? null : codes.findAll().stream()
			.filter(c -> c.isPersonal() && c.getSuName().equalsIgnoreCase(suName)).findFirst().orElse(null);
	}

	/** The lowest a stylist is paid when their matrix rate is lower. */
	public BigDecimal minimum() {
		return money(MINIMUM_KEY, DEFAULT_MINIMUM);
	}

	public PayRules setMinimum(BigDecimal amount) {
		return saveRate(MINIMUM_KEY, amount);
	}

	public PayRules setReceptionistRate(BigDecimal amount) {
		return saveRate(RECEPTIONIST_KEY, amount);
	}

	private PayRules saveRate(String key, BigDecimal amount) {
		if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("100")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a rate more than $0");
		}
		settings.save(new AppSetting(key, amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
		return rules();
	}

	private BigDecimal money(String key, BigDecimal fallback) {
		return settings.findById(key).map(s -> {
			try {
				return new BigDecimal(s.getValue());
			} catch (NumberFormatException e) {
				return fallback;
			}
		}).orElse(fallback);
	}

	/** The site role of the active account linked to this Salon Ultimate name, or null if none is linked. */
	public Role roleOf(String suName) {
		if (suName == null) return null;
		return users.findAll().stream()
			.filter(u -> u.isActive() && suName.equalsIgnoreCase(u.getSalonUltimateName()))
			.map(User::getRoles).findFirst().orElse(null);
	}

	/** What this role adds to the matrix rate per hour: the lead or manager bump, 0 for everyone else. */
	public BigDecimal bump(Role role) {
		if (role == Role.LEAD) return kindBump("LEAD");
		if (role == Role.MANAGER) return kindBump("MANAGER");
		return BigDecimal.ZERO;
	}

	/** The amount the owner set for one kind of bump (LEAD, LEAD_PROD, MANAGER). */
	public BigDecimal kindBump(String kind) {
		String key = BUMP_KEYS.get(kind);
		return key == null ? BigDecimal.ZERO : money(key, DEFAULT_BUMPS.get(kind));
	}

	/** Someone's bump: what the owner picked for them, or their role's if nothing was picked. */
	BigDecimal personBump(PayCode row, Role role) {
		String kind = row == null ? null : row.getBumpKind();
		if (kind == null) return bump(role);
		if (kind.equals("NONE")) return BigDecimal.ZERO;
		if (kind.equals("CUSTOM")) return row.getBump() == null ? BigDecimal.ZERO : row.getBump();
		return kindBump(kind);
	}

	public PayRules setBump(String kind, BigDecimal amount) {
		String key = kind == null ? null : BUMP_KEYS.get(kind);
		if (key == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the lead, lead/prod, or manager bump");
		if (amount == null || amount.signum() < 0 || amount.compareTo(BigDecimal.TEN) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a bump from $0 to $10");
		}
		settings.save(new AppSetting(key, amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
		return rules();
	}

	/**
	 * Set someone's own base rate. A null rate, or one equal to their role's default, clears it so
	 * they follow the default (and any later change to it).
	 */
	@Transactional
	public PayRules setBaseRate(PayPersonInput input) {
		String name = input == null || input.name() == null ? "" : input.name().trim();
		if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick who this is for");
		BigDecimal flat = input.baseRate();
		if (flat != null && (flat.signum() <= 0 || flat.compareTo(new BigDecimal("500")) > 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rate has to be more than $0");
		}
		if (flat != null && flat.compareTo(defaultRate(roleOf(name))) == 0) flat = null;
		BigDecimal rate = flat == null ? null : flat.setScale(2, RoundingMode.HALF_UP);
		savePersonal(name, row -> row.setFlatRate(rate));
		return rules();
	}

	/** Choose someone's bump: follow their role (null or ROLE), none, a named kind, or their own amount. */
	@Transactional
	public PayRules setPersonBump(PersonBumpInput input) {
		String name = input == null || input.name() == null ? "" : input.name().trim();
		if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick who this is for");
		String kind = input.kind() == null || input.kind().equals("ROLE") ? null : input.kind();
		if (kind != null && !PERSON_BUMPS.contains(kind)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a bump");
		BigDecimal amount = input.amount();
		if ("CUSTOM".equals(kind) && (amount == null || amount.signum() < 0 || amount.compareTo(BigDecimal.TEN) > 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a bump from $0 to $10");
		}
		BigDecimal own = "CUSTOM".equals(kind) ? amount.setScale(2, RoundingMode.HALF_UP) : null;
		savePersonal(name, row -> {
			row.setBumpKind(kind);
			row.setBump(own);
		});
		return rules();
	}

	/**
	 * Change someone's row of personal settings, creating it if needed and deleting it when
	 * nothing is left in it. Old rows from the codes file are folded in (their premium is unused).
	 */
	private void savePersonal(String name, java.util.function.Consumer<PayCode> change) {
		List<PayCode> old = codes.findAll().stream().filter(c -> c.getSuName().equalsIgnoreCase(name)).toList();
		PayCode row = old.stream().filter(PayCode::isPersonal).findFirst().orElse(null);
		PayCode keep = new PayCode(old.isEmpty() ? name : old.get(0).getSuName(), null, null, row == null ? null : row.getFlatRate());
		if (row != null) {
			keep.setBumpKind(row.getBumpKind());
			keep.setBump(row.getBump());
		}
		change.accept(keep);
		if (!old.isEmpty()) {
			codes.deleteAll(old);
			codes.flush();
		}
		if (keep.isPersonal()) codes.save(keep);
	}

	/**
	 * Take someone off the pay list: clears any base rate set just for them and hides them
	 * until Salon Ultimate reports them in a week that starts after today.
	 */
	@Transactional
	public PayRules remove(String name, LocalDate today) {
		String n = name == null ? "" : name.trim();
		if (n.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick who to remove");
		List<PayCode> old = codes.findAll().stream().filter(c -> c.getSuName().equalsIgnoreCase(n)).toList();
		codes.deleteAll(old);
		String key = REMOVED_PREFIX + n.toLowerCase();
		if (key.length() > 100) key = key.substring(0, 100);
		settings.save(new AppSetting(key, today.toString()));
		return rules();
	}

	/**
	 * A stylist's rate: the highest matrix step at or below PPH, but never under their base rate
	 * ({@code minimum}), plus their lead or manager bump (0 for everyone else).
	 */
	static BigDecimal rate(BigDecimal pph, List<PayMatrixRow> steps, BigDecimal minimum, BigDecimal bump) {
		BigDecimal base = minimum;
		for (PayMatrixRow step : steps) {
			if (pph.compareTo(step.getPph()) >= 0) base = step.getRate();
			else break;
		}
		return base.max(minimum).add(bump).setScale(2, RoundingMode.HALF_UP);
	}

	/** Replace the matrix with the skill's matrix.json. */
	@Transactional
	public PayRules load(PayRulesUpload upload) {
		if (upload == null || upload.matrix() == null || upload.matrix().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The matrix file has no rows");
		}
		for (PayMatrixEntry e : upload.matrix()) {
			if (e.pph() == null || e.rate() == null || e.pph().signum() < 0 || e.rate().signum() <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every matrix row needs a PPH and a rate");
			}
		}
		Map<BigDecimal, PayMatrixEntry> unique = upload.matrix().stream()
			.collect(Collectors.toMap(e -> e.pph().setScale(2, RoundingMode.HALF_UP), Function.identity(), (a, b) -> b));
		matrix.deleteAllInBatch();
		matrix.saveAll(unique.entrySet().stream().map(e -> new PayMatrixRow(e.getKey(), e.getValue().rate())).toList());
		return rules();
	}

	/** The matrix and settings, plus everyone to show on the owner's pay table: anyone with a base rate of their own or seen in Salon Ultimate. */
	public PayRules rules() {
		Map<String, User> linked = new java.util.HashMap<>();
		users.findAll().stream().filter(u -> u.isActive() && u.getSalonUltimateName() != null)
			.forEach(u -> linked.putIfAbsent(u.getSalonUltimateName().toLowerCase(), u));
		List<PayCode> flats = codes.findAll().stream().filter(PayCode::isPersonal).toList();
		java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		flats.forEach(c -> names.add(c.getSuName()));
		// Everyone Salon Ultimate has reported, except people the owner removed who haven't been
		// in a week that started after they were removed.
		Map<String, LocalDate> removed = new java.util.HashMap<>();
		settings.findAll().stream().filter(s -> s.getKey().startsWith(REMOVED_PREFIX)).forEach(s -> {
			try {
				removed.put(s.getKey().substring(REMOVED_PREFIX.length()), LocalDate.parse(s.getValue()));
			} catch (RuntimeException e) {
				// A bad date: ignore it.
			}
		});
		stats.lastSeen().stream()
			.filter(l -> {
				LocalDate gone = removed.get(l.getName().toLowerCase());
				return gone == null || l.getLastWeek().isAfter(gone);
			})
			.forEach(l -> names.add(l.getName()));
		List<PayPerson> people = names.stream()
			.map(n -> {
				PayCode c = flats.stream().filter(x -> x.getSuName().equalsIgnoreCase(n)).findFirst().orElse(null);
				User u = linked.get(n.toLowerCase());
				Role role = u == null ? null : u.getRoles();
				BigDecimal def = defaultRate(role).setScale(2, RoundingMode.HALF_UP);
				BigDecimal own = c == null || c.getFlatRate() == null ? null : c.getFlatRate().setScale(2, RoundingMode.HALF_UP);
				BigDecimal base = own != null ? own : def;
				BigDecimal bump = personBump(c, role).setScale(2, RoundingMode.HALF_UP);
				return new PayPerson(n, role, own, def, base.add(bump).setScale(2, RoundingMode.HALF_UP),
					c == null ? null : c.getBumpKind(), bump);
			})
			.sorted(Comparator.comparing((PayPerson p) -> lastFirst(p.name()), String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new PayRules(
			sortedMatrix().stream().map(r -> new PayMatrixEntry(r.getPph(), r.getRate())).toList(),
			minimum(),
			defaultRate(Role.RECEPTIONIST),
			kindBump("LEAD"),
			kindBump("LEAD_PROD"),
			kindBump("MANAGER"),
			people);
	}

	/** "Ava Stone" becomes "Stone, Ava", the way the payroll skill lists people. */
	public static String lastFirst(String suName) {
		String n = suName == null ? "" : suName.trim();
		int space = n.lastIndexOf(' ');
		return space < 0 ? n : n.substring(space + 1) + ", " + n.substring(0, space);
	}

	private List<PayMatrixRow> sortedMatrix() {
		return matrix.findAll().stream().sorted(Comparator.comparing(PayMatrixRow::getPph)).toList();
	}

	private static BigDecimal orZero(BigDecimal n) {
		return n == null ? BigDecimal.ZERO : n;
	}
}
