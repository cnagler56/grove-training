package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AdpPerson;
import com.grove.Domain.AppSetting;
import com.grove.Domain.Dtos.AdpFile;
import com.grove.Domain.Dtos.AdpFilePerson;
import com.grove.Domain.Dtos.AdpHandLine;
import com.grove.Domain.Dtos.AdpLine;
import com.grove.Domain.Dtos.AdpPersonInput;
import com.grove.Domain.Dtos.AdpPersonView;
import com.grove.Domain.Dtos.AdpSettings;
import com.grove.Domain.Dtos.PayPeriod;
import com.grove.Domain.Dtos.PayPerson;
import com.grove.Domain.Dtos.PayrollEmployee;
import com.grove.Domain.Dtos.PayrollNoteView;
import com.grove.Domain.Dtos.PayrollPayRow;
import com.grove.Domain.Dtos.PayrollPayWeek;
import com.grove.Domain.Dtos.PayrollReport;
import com.grove.Domain.Dtos.PayrollSummary;
import com.grove.Domain.Dtos.PayrollWeek;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.AdpPersonRepository;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.UserRepository;

/**
 * The file the owner imports into ADP RUN (Home, Time Sheet Import) so payroll isn't typed
 * by hand. It's ADP's "##GENERIC## V1.0" time sheet format, keyed by ADP employee ID.
 *
 * <p>ADP's import can't carry a pay rate: imported hours are paid at the rate on the
 * employee's ADP profile (tested on ADP in Nov 2026). So the file only has hours paid at
 * their standard rate ({@link PayService#standardRate}), which is what their ADP profile
 * has. Hours at any other rate (most matrix weeks) are listed to type in by hand, as are
 * payroll-note additions and deductions. Tips, commission, and the monthly bonus are
 * dollar amounts, which import as they are.
 */
@Service
public class AdpService {

	static final String COMPANY_KEY = "adp.companyCode";
	static final String VERSION = "##GENERIC## V1.0";
	static final String HEADER = "IID,Pay Frequency,Pay Period Start,Pay Period End,Employee Id,Earnings Code,Pay Hours,Dollars,"
		+ "Separate Check,Worked In Dept,Rate Code";
	/** Everyone at the salon is paid every two weeks. */
	static final String BIWEEKLY = "B";
	private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
	private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("MMddyyyy");
	private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]{1,15}");
	private static final Pattern EMPLOYEE_ID = Pattern.compile("[A-Za-z0-9]{1,10}");
	private static final BigDecimal MAX_BONUS = new BigDecimal("10000");

	private final AdpPersonRepository adp;
	private final AppSettingRepository settings;
	private final PayService pay;
	private final PayrollService payroll;
	private final UserRepository users;

	public AdpService(AdpPersonRepository adp, AppSettingRepository settings, PayService pay, PayrollService payroll,
			UserRepository users) {
		this.adp = adp;
		this.settings = settings;
		this.pay = pay;
		this.payroll = payroll;
		this.users = users;
	}

	/** Everyone on the pay table (and anyone with ADP details saved), with their ADP details. */
	public AdpSettings settings() {
		Map<String, AdpPerson> rows = rows();
		Map<String, Role> roles = new HashMap<>();
		TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (PayPerson p : pay.rules().people()) {
			names.add(p.name());
			roles.put(p.name().toLowerCase(), p.role());
		}
		rows.values().forEach(r -> names.add(r.getSuName()));
		List<AdpPersonView> people = names.stream()
			.map(n -> {
				AdpPerson r = rows.get(n.toLowerCase());
				Role role = roles.containsKey(n.toLowerCase()) ? roles.get(n.toLowerCase()) : pay.roleOf(n);
				return new AdpPersonView(n, role, r == null ? null : r.getEmployeeId(), r != null && r.isSalaried(),
					r == null ? null : r.getMonthlyBonus(), pay.standardRate(n));
			})
			.sorted(Comparator.comparing((AdpPersonView v) -> PayService.lastFirst(v.name()), String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new AdpSettings(companyCode(), people);
	}

	/** ADP's company code (IID) for the file; blank clears it. */
	public AdpSettings setCompanyCode(String code) {
		String c = code == null ? "" : code.trim();
		if (c.isEmpty()) {
			settings.findById(COMPANY_KEY).ifPresent(settings::delete);
		} else {
			if (!CODE.matcher(c).matches()) throw bad("The company code is letters and numbers only, like 12345678");
			settings.save(new AppSetting(COMPANY_KEY, c));
		}
		return settings();
	}

	/** Save one person's ADP details; a row with nothing left in it is deleted. */
	@Transactional
	public AdpSettings setPerson(AdpPersonInput input) {
		String name = input == null || input.name() == null ? "" : input.name().trim();
		if (name.isEmpty()) throw bad("Pick who this is for");
		String id = input.employeeId() == null ? null : input.employeeId().trim();
		if (id != null && id.isEmpty()) id = null;
		if (id != null && !EMPLOYEE_ID.matcher(id).matches()) throw bad("An ADP employee ID is letters and numbers only, like 101");
		BigDecimal bonus = input.monthlyBonus();
		if (bonus != null && bonus.signum() == 0) bonus = null;
		if (bonus != null && (bonus.signum() < 0 || bonus.compareTo(MAX_BONUS) > 0)) throw bad("Enter a bonus from $0 to $10,000");
		if (bonus != null && bonus.stripTrailingZeros().scale() > 2) throw bad("A bonus can have at most two decimal places (cents)");

		List<AdpPerson> all = adp.findAll();
		AdpPerson row = all.stream().filter(r -> r.getSuName().equalsIgnoreCase(name)).findFirst().orElse(new AdpPerson(name));
		if (id != null) {
			String taken = id;
			all.stream()
				.filter(r -> !r.getSuName().equalsIgnoreCase(name) && taken.equalsIgnoreCase(r.getEmployeeId()))
				.findFirst()
				.ifPresent(r -> { throw bad("ADP employee ID " + taken + " is already " + r.getSuName() + "'s"); });
		}
		row.setEmployeeId(id);
		row.setSalaried(Boolean.TRUE.equals(input.salaried()));
		row.setMonthlyBonus(bonus == null ? null : bonus.setScale(2, RoundingMode.HALF_UP));
		if (row.isEmpty()) {
			if (adp.existsById(row.getSuName())) adp.delete(row);
		} else {
			adp.save(row);
		}
		return settings();
	}

	/** True when no other check falls in the same month after this one. */
	public static boolean lastCheckOfMonth(LocalDate checkDate) {
		return checkDate.plusDays(14).getMonthValue() != checkDate.getMonthValue();
	}

	/** What the file for this check would hold, plus anything that has to be fixed first. */
	public AdpFile file(LocalDate checkDate) {
		PayrollReport report = payroll.report(checkDate);
		PayrollSummary summary = payroll.summary(checkDate);
		PayPeriod period = PayrollService.period(checkDate);
		boolean lastOfMonth = lastCheckOfMonth(checkDate);
		String company = companyCode();
		Map<String, AdpPerson> rows = rows();
		List<String> problems = new ArrayList<>();
		List<String> notices = new ArrayList<>();

		if (company == null) problems.add("Add your ADP company code on Settings, in the ADP import file card.");
		if (!report.ratesLoaded()) problems.add("Load the pay matrix on Settings first.");
		for (int i = 0; i < report.weeks().size(); i++) {
			PayrollWeek w = report.weeks().get(i);
			if (w.pull() == null || !"OK".equals(w.pull().status())) {
				String why = w.pull() == null || w.pull().message() == null ? "" : " (" + w.pull().message() + ")";
				problems.add("Week " + (i + 1) + " hasn't been pulled from Salon Ultimate yet" + why + ". Use Pull on this page.");
			}
		}

		// Paid time off and notes belong to site accounts; match them to Salon Ultimate names.
		Map<Long, String> suNames = new HashMap<>();
		for (User u : users.findAll()) {
			if (u.getSalonUltimateName() != null) suNames.put(u.getUserId(), u.getSalonUltimateName());
		}
		Map<String, PayrollEmployee> extras = new HashMap<>();
		List<AdpFilePerson> people = new ArrayList<>();
		for (PayrollEmployee e : summary.employees()) {
			String su = suNames.get(e.userId());
			if (su != null) {
				extras.put(su.toLowerCase(), e);
			} else if (e.ptoHours().signum() > 0 || e.pendingPtoHours().signum() > 0) {
				problems.add(e.name() + " has paid time off but isn't linked to a Salon Ultimate name. Link them on the Team page.");
			} else {
				people.add(new AdpFilePerson(PayService.lastFirst(e.name()), null, false, null, List.of(), notes(e)));
			}
		}

		for (PayrollPayRow r : report.rows()) {
			PayrollEmployee e = extras.remove(r.suName().toLowerCase());
			if (r.problem() != null) problems.add(r.name() + ": " + r.problem());
			people.add(person(r.suName(), r.week1(), r.week2(), e, rows.get(r.suName().toLowerCase()), lastOfMonth, problems));
		}
		// Paid time off or notes for someone with no hours this period.
		for (Map.Entry<String, PayrollEmployee> left : extras.entrySet()) {
			String su = suNames.values().stream().filter(n -> n.equalsIgnoreCase(left.getKey())).findFirst().orElse(left.getKey());
			people.add(person(su, null, null, left.getValue(), rows.get(left.getKey()), lastOfMonth, problems));
		}
		if (lastOfMonth) {
			for (AdpPerson a : rows.values()) {
				boolean listed = people.stream().anyMatch(p -> p.name().equalsIgnoreCase(PayService.lastFirst(a.getSuName())));
				if (a.getMonthlyBonus() != null && !listed) {
					notices.add(a.getSuName() + " gets a monthly bonus but has no hours this pay period, so it isn't in the file.");
				}
			}
		}
		people.sort(Comparator.comparing(AdpFilePerson::name, String.CASE_INSENSITIVE_ORDER));

		BigDecimal reg = BigDecimal.ZERO, vac = BigDecimal.ZERO, tips = BigDecimal.ZERO, comm = BigDecimal.ZERO, bon = BigDecimal.ZERO;
		for (AdpFilePerson p : people) {
			for (AdpLine l : p.lines()) {
				switch (l.earningsCode()) {
					case "REG" -> reg = reg.add(l.hours());
					case "VAC" -> vac = vac.add(l.hours());
					case "CASHTIP" -> tips = tips.add(l.dollars());
					case "COMM" -> comm = comm.add(l.dollars());
					case "BON" -> bon = bon.add(l.dollars());
					default -> { }
				}
			}
		}
		String fileName = "Grove-ADP-Biweekly-" + FILE_DATE.format(period.workStart()) + "-" + FILE_DATE.format(period.workEnd()) + ".csv";
		return new AdpFile(period, company, lastOfMonth, fileName, problems, notices, people, reg, vac, tips, comm, bon);
	}

	/** The file itself, or a 409 when something has to be fixed first. */
	public static String csvOf(AdpFile f) {
		if (!f.problems().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Fix these first: " + String.join(" ", f.problems()));
		}
		String start = US_DATE.format(f.period().workStart());
		String end = US_DATE.format(f.period().workEnd());
		StringBuilder out = new StringBuilder(VERSION).append("\r\n").append(HEADER).append("\r\n");
		for (AdpFilePerson p : f.people()) {
			for (AdpLine l : p.lines()) {
				out.append(f.companyCode()).append(',').append(BIWEEKLY).append(',').append(start).append(',').append(end).append(',')
					.append(p.employeeId()).append(',').append(l.earningsCode()).append(',')
					.append(amount(l.hours())).append(',').append(amount(l.dollars()))
					.append(",0,,BASE\r\n");
			}
		}
		return out.toString();
	}

	/**
	 * One person's lines. Hourly people: hours at their standard rate go in the file as REG, every
	 * other rate is typed by hand, and approved paid time off goes in as VAC. Salaried people: ADP
	 * pays their fixed amount, so the file has their hours only.
	 */
	private AdpFilePerson person(String su, PayrollPayWeek w1, PayrollPayWeek w2, PayrollEmployee e, AdpPerson a,
			boolean lastOfMonth, List<String> problems) {
		String name = PayService.lastFirst(su);
		boolean salaried = a != null && a.isSalaried();
		BigDecimal standard = pay.standardRate(su);
		BigDecimal pto = e == null ? BigDecimal.ZERO : e.ptoHours();
		List<AdpLine> lines = new ArrayList<>();
		List<AdpHandLine> byHand = new ArrayList<>();

		BigDecimal offFloor = value(w1, PayrollPayWeek::offFloorHours).add(value(w2, PayrollPayWeek::offFloorHours));
		if (salaried) {
			BigDecimal all = value(w1, PayrollPayWeek::floorHours).add(value(w2, PayrollPayWeek::floorHours)).add(offFloor);
			if (all.signum() > 0) lines.add(new AdpLine("REG", all, null));
			if (pto.signum() > 0) byHand.add(new AdpHandLine("VACATION", pto, null, null, "Fixed pay: enter paid time off the way you do now"));
		} else {
			// Hours by the rate they're paid at; weeks at the same rate are one line, like in ADP.
			Map<BigDecimal, BigDecimal> byRate = new TreeMap<>();
			addAt(byRate, w1 == null ? null : w1.rate(), value(w1, PayrollPayWeek::floorHours), byHand);
			addAt(byRate, w2 == null ? null : w2.rate(), value(w2, PayrollPayWeek::floorHours), byHand);
			addAt(byRate, standard, offFloor, byHand);
			BigDecimal imported = BigDecimal.ZERO;
			for (Map.Entry<BigDecimal, BigDecimal> at : byRate.entrySet()) {
				if (at.getKey().compareTo(standard) == 0) imported = imported.add(at.getValue());
				else byHand.add(new AdpHandLine("REGULAR", at.getValue(), at.getKey(), null, null));
			}
			if (imported.signum() > 0) lines.add(new AdpLine("REG", imported, null));
			if (pto.signum() > 0) lines.add(new AdpLine("VAC", pto, null));
		}

		BigDecimal tips = value(w1, PayrollPayWeek::tips).add(value(w2, PayrollPayWeek::tips));
		if (tips.signum() > 0) lines.add(new AdpLine("CASHTIP", null, tips.setScale(2, RoundingMode.HALF_UP)));
		BigDecimal comm = value(w1, PayrollPayWeek::commission).add(value(w2, PayrollPayWeek::commission));
		if (comm.signum() > 0) lines.add(new AdpLine("COMM", null, comm.setScale(2, RoundingMode.HALF_UP)));
		if (lastOfMonth && a != null && a.getMonthlyBonus() != null) lines.add(new AdpLine("BON", null, a.getMonthlyBonus()));
		if (e != null) byHand.addAll(notes(e));

		if (e != null && e.pendingPtoHours().signum() > 0) {
			problems.add(name + " has " + e.pendingPtoHours().stripTrailingZeros().toPlainString()
				+ " hours of paid time off waiting for an answer. Approve or decline it first.");
		}
		String id = a == null ? null : a.getEmployeeId();
		if (!lines.isEmpty() && id == null) problems.add("Add " + su + "'s ADP employee ID on Settings.");
		boolean hasHours = lines.stream().anyMatch(l -> l.hours() != null);
		return new AdpFilePerson(name, id, salaried, hasHours && !salaried ? standard : null, lines, byHand);
	}

	/** Additions and deductions from payroll notes: the file can't carry them. */
	private static List<AdpHandLine> notes(PayrollEmployee e) {
		List<AdpHandLine> out = new ArrayList<>();
		for (PayrollNoteView n : e.notes()) {
			if (n.amount() == null || !("ADDITION".equals(n.kind()) || "DEDUCTION".equals(n.kind()))) continue;
			out.add(new AdpHandLine(n.kind(), null, null, n.amount(), n.note()));
		}
		return out;
	}

	/** Add hours at a rate; with no rate (no pay matrix yet) they can only be typed by hand. */
	private static void addAt(Map<BigDecimal, BigDecimal> byRate, BigDecimal rate, BigDecimal hours, List<AdpHandLine> byHand) {
		if (hours.signum() <= 0) return;
		if (rate == null) {
			byHand.add(new AdpHandLine("REGULAR", hours, null, null, "No rate yet"));
			return;
		}
		byRate.merge(rate.setScale(2, RoundingMode.HALF_UP), hours, BigDecimal::add);
	}

	private static BigDecimal value(PayrollPayWeek w, java.util.function.Function<PayrollPayWeek, BigDecimal> f) {
		if (w == null) return BigDecimal.ZERO;
		BigDecimal n = f.apply(w);
		return n == null ? BigDecimal.ZERO : n;
	}

	private static String amount(BigDecimal n) {
		return n == null ? "" : n.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String companyCode() {
		return settings.findById(COMPANY_KEY).map(AppSetting::getValue).filter(v -> !v.isBlank()).orElse(null);
	}

	/** Everyone's ADP row, by lowercase Salon Ultimate name. */
	private Map<String, AdpPerson> rows() {
		Map<String, AdpPerson> out = new HashMap<>();
		adp.findAll().forEach(r -> out.put(r.getSuName().toLowerCase(), r));
		return out;
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
