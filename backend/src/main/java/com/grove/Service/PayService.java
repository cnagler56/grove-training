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
import com.grove.Domain.Dtos.PayCodeEntry;
import com.grove.Domain.Dtos.PayPerson;
import com.grove.Domain.Dtos.PayPersonInput;
import com.grove.Domain.Dtos.PayCodeView;
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
 * Weekly hourly pay, figured exactly like the payroll skill (calculate_payroll.py):
 * <ul>
 * <li>sales = after-discount service sales + 20% of the discount amount</li>
 * <li>PPH = sales ÷ floor hours</li>
 * <li>rate = the matrix rate for the highest step at or below PPH ($16 if none),
 *     plus the person's code premium, but never under $16 ($13 for a negative premium)</li>
 * <li>leads and managers (by their role on the site) get the owner's bump for that role on top:
 *     $0.50 for leads and $1.00 for managers to start</li>
 * <li>a flat rate in their code replaces all of that, bump included</li>
 * </ul>
 * The matrix and codes come from the skill's files, loaded by the owner and
 * kept only in the database (the code repository is public).
 */
@Service
public class PayService {

	static final BigDecimal BASE_RATE = new BigDecimal("16.00");
	static final BigDecimal MIN_RATE_WITH_DEDUCTION = new BigDecimal("13.00");
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
		PayCode code = code(suName);
		BigDecimal bump = bump(roleOf(suName));
		return s -> rate(exactPph(s), code, steps, bump);
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

	/** Their pay code, matched on the Salon Ultimate name; null if they have none. */
	public PayCode code(String suName) {
		return suName == null ? null : codes.findAll().stream()
			.filter(c -> c.getSuName().equalsIgnoreCase(suName)).findFirst().orElse(null);
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
		BigDecimal fallback = DEFAULT_BUMPS.get(role);
		return settings.findById(key).map(s -> {
			try {
				return new BigDecimal(s.getValue());
			} catch (NumberFormatException e) {
				return fallback;
			}
		}).orElse(fallback);
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

	/** Add or change one person's pay: a premium on the matrix, or a flat hourly rate. */
	@Transactional
	public PayRules savePerson(PayPersonInput input) {
		String name = input == null || input.name() == null ? "" : input.name().trim();
		if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick who this is for");
		BigDecimal premium = input.premium();
		BigDecimal flat = input.flatRate();
		if (flat != null && (flat.signum() <= 0 || flat.compareTo(new BigDecimal("500")) > 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A flat rate has to be more than $0");
		}
		if (premium != null && premium.abs().compareTo(new BigDecimal("100")) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That premium is too large");
		}
		PayCode existing = code(name);
		String key = existing != null ? existing.getSuName() : name;
		if (existing != null) {
			codes.delete(existing);
			codes.flush();
		}
		codes.save(new PayCode(key, input.code(), flat != null ? null : (premium == null ? BigDecimal.ZERO : premium), flat));
		return rules();
	}

	public PayRules removePerson(String name) {
		PayCode existing = code(name);
		if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pay code for that name");
		codes.delete(existing);
		return rules();
	}

	static BigDecimal rate(BigDecimal pph, PayCode code, List<PayMatrixRow> steps) {
		return rate(pph, code, steps, BigDecimal.ZERO);
	}

	/** {@code bump} is the lead or manager bump (0 for everyone else); it goes on after the minimum, and never on a flat rate. */
	static BigDecimal rate(BigDecimal pph, PayCode code, List<PayMatrixRow> steps, BigDecimal bump) {
		if (code != null && code.getFlatRate() != null) return code.getFlatRate().setScale(2, RoundingMode.HALF_UP);
		BigDecimal base = BASE_RATE;
		for (PayMatrixRow step : steps) {
			if (pph.compareTo(step.getPph()) >= 0) base = step.getRate();
			else break;
		}
		BigDecimal premium = code == null || code.getPremium() == null ? BigDecimal.ZERO : code.getPremium();
		BigDecimal min = premium.signum() < 0 ? MIN_RATE_WITH_DEDUCTION : BASE_RATE;
		return base.add(premium).max(min).add(bump).setScale(2, RoundingMode.HALF_UP);
	}

	/** Replace the matrix and codes with the skill's files. */
	@Transactional
	public PayRules load(PayRulesUpload upload) {
		if (upload == null || upload.matrix() == null || upload.matrix().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The matrix file has no rows");
		}
		if (upload.codes() == null || upload.codes().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The employee codes file has no one in it");
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

		codes.deleteAllInBatch();
		codes.saveAll(upload.codes().entrySet().stream()
			.map(e -> {
				PayCodeEntry c = e.getValue();
				return new PayCode(salonUltimateName(e.getKey()), c.code(), c.premium(), c.flatRate());
			})
			.collect(Collectors.toMap(c -> c.getSuName().toLowerCase(), Function.identity(), (a, b) -> b))
			.values());
		return rules();
	}

	/** What's loaded, plus everyone to show on the owner's pay table: anyone with a code or seen in Salon Ultimate. */
	public PayRules rules() {
		Map<String, User> linked = new java.util.HashMap<>();
		users.findAll().stream().filter(u -> u.isActive() && u.getSalonUltimateName() != null)
			.forEach(u -> linked.putIfAbsent(u.getSalonUltimateName().toLowerCase(), u));
		List<PayCode> all = codes.findAll();
		java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		all.forEach(c -> names.add(c.getSuName()));
		names.addAll(stats.names());
		List<PayPerson> people = names.stream()
			.map(n -> {
				PayCode c = all.stream().filter(x -> x.getSuName().equalsIgnoreCase(n)).findFirst().orElse(null);
				User u = linked.get(n.toLowerCase());
				return new PayPerson(n, u == null ? null : u.getRoles(), c != null,
					c == null ? null : c.getCode(), c == null ? null : c.getPremium(), c == null ? null : c.getFlatRate());
			})
			.sorted(Comparator.comparing((PayPerson p) -> lastFirst(p.name()), String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new PayRules(
			sortedMatrix().stream().map(r -> new PayMatrixEntry(r.getPph(), r.getRate())).toList(),
			all.stream()
				.sorted(Comparator.comparing(c -> c.getSuName().toLowerCase()))
				.map(c -> new PayCodeView(c.getSuName(), c.getCode(), c.getPremium(), c.getFlatRate()))
				.toList(),
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

	/** "De La Cruz, Mia" (the codes file) → "Mia De La Cruz" (Salon Ultimate). */
	static String salonUltimateName(String fileName) {
		int comma = fileName.indexOf(',');
		if (comma < 0) return fileName.trim();
		return (fileName.substring(comma + 1).trim() + " " + fileName.substring(0, comma).trim()).trim();
	}

	private List<PayMatrixRow> sortedMatrix() {
		return matrix.findAll().stream().sorted(Comparator.comparing(PayMatrixRow::getPph)).toList();
	}

	private static BigDecimal orZero(BigDecimal n) {
		return n == null ? BigDecimal.ZERO : n;
	}
}
