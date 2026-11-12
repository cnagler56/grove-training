package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * <li>stylists: the matrix rate for the highest step at or below PPH, or the owner's minimum
 *     ($16 to start), whichever is higher</li>
 * <li>leads and managers (by their role on the site) get the owner's bump for that role on top:
 *     $0.50 for leads and $1.00 for managers to start</li>
 * <li>someone with a flat rate (every receptionist) gets exactly that instead</li>
 * </ul>
 * The matrix comes from the payroll skill's matrix.json, loaded by the owner; it and the
 * flat rates are kept only in the database (the code repository is public). Flat rates
 * live in the old pay_codes table; its code and premium columns are no longer used.
 */
@Service
public class PayService {

	static final BigDecimal DEFAULT_MINIMUM = new BigDecimal("16.00");
	static final String MINIMUM_KEY = "pay.minimumRate";
	static final BigDecimal DISCOUNT_SHARE = new BigDecimal("0.2");
	/** The roles that get a bump on the matrix rate, and where each amount is kept. */
	static final Map<Role, String> BUMP_KEYS = Map.of(Role.LEAD, "pay.leadBump", Role.MANAGER, "pay.managerBump");
	static final Map<Role, BigDecimal> DEFAULT_BUMPS = Map.of(Role.LEAD, new BigDecimal("0.50"), Role.MANAGER, new BigDecimal("1.00"));
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
		BigDecimal flat = flatRate(suName);
		if (flat != null) return s -> flat.setScale(2, RoundingMode.HALF_UP);
		Role role = roleOf(suName);
		if (role == Role.RECEPTIONIST) return s -> null; // no flat rate set yet
		BigDecimal minimum = minimum();
		BigDecimal bump = bump(role);
		return s -> rate(exactPph(s), steps, minimum, bump);
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

	/** Their flat hourly rate, matched on the Salon Ultimate name; null if they're paid on the matrix. */
	public BigDecimal flatRate(String suName) {
		PayCode row = flatRow(suName);
		return row == null ? null : row.getFlatRate();
	}

	private PayCode flatRow(String suName) {
		return suName == null ? null : codes.findAll().stream()
			.filter(c -> c.getFlatRate() != null && c.getSuName().equalsIgnoreCase(suName)).findFirst().orElse(null);
	}

	/** The lowest a stylist is paid when their matrix rate is lower. */
	public BigDecimal minimum() {
		return money(MINIMUM_KEY, DEFAULT_MINIMUM);
	}

	public PayRules setMinimum(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("100")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a minimum rate more than $0");
		}
		settings.save(new AppSetting(MINIMUM_KEY, amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
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
		String key = role == null ? null : BUMP_KEYS.get(role);
		if (key == null) return BigDecimal.ZERO;
		return money(key, DEFAULT_BUMPS.get(role));
	}

	public PayRules setBump(Role role, BigDecimal amount) {
		String key = role == null ? null : BUMP_KEYS.get(role);
		if (key == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only leads and managers have a bump");
		if (amount == null || amount.signum() < 0 || amount.compareTo(BigDecimal.TEN) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a bump from $0 to $10");
		}
		settings.save(new AppSetting(key, amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
		return rules();
	}

	/** Give someone a flat hourly rate, or with a null rate, put them back on the matrix. */
	@Transactional
	public PayRules setFlatRate(PayPersonInput input) {
		String name = input == null || input.name() == null ? "" : input.name().trim();
		if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick who this is for");
		BigDecimal flat = input.flatRate();
		if (flat != null && (flat.signum() <= 0 || flat.compareTo(new BigDecimal("500")) > 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A flat rate has to be more than $0");
		}
		// Clear any old row for this name, flat or from the codes file.
		List<PayCode> old = codes.findAll().stream().filter(c -> c.getSuName().equalsIgnoreCase(name)).toList();
		String key = old.isEmpty() ? name : old.get(0).getSuName();
		if (!old.isEmpty()) {
			codes.deleteAll(old);
			codes.flush();
		}
		if (flat != null) codes.save(new PayCode(key, null, null, flat.setScale(2, RoundingMode.HALF_UP)));
		return rules();
	}

	/**
	 * A stylist's rate: the highest matrix step at or below PPH, but never under the minimum,
	 * plus their lead or manager bump (0 for everyone else).
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

	/** The matrix and settings, plus everyone to show on the owner's pay table: anyone with a flat rate or seen in Salon Ultimate. */
	public PayRules rules() {
		Map<String, User> linked = new java.util.HashMap<>();
		users.findAll().stream().filter(u -> u.isActive() && u.getSalonUltimateName() != null)
			.forEach(u -> linked.putIfAbsent(u.getSalonUltimateName().toLowerCase(), u));
		List<PayCode> flats = codes.findAll().stream().filter(c -> c.getFlatRate() != null).toList();
		java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		flats.forEach(c -> names.add(c.getSuName()));
		names.addAll(stats.names());
		List<PayPerson> people = names.stream()
			.map(n -> {
				PayCode c = flats.stream().filter(x -> x.getSuName().equalsIgnoreCase(n)).findFirst().orElse(null);
				User u = linked.get(n.toLowerCase());
				return new PayPerson(n, u == null ? null : u.getRoles(), c == null ? null : c.getFlatRate());
			})
			.sorted(Comparator.comparing((PayPerson p) -> lastFirst(p.name()), String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new PayRules(
			sortedMatrix().stream().map(r -> new PayMatrixEntry(r.getPph(), r.getRate())).toList(),
			minimum(),
			bump(Role.LEAD),
			bump(Role.MANAGER),
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
