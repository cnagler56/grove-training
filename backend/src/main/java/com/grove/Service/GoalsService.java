package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.DailySales;
import com.grove.Domain.Role;
import com.grove.Domain.Shift;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.DailySalesRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.UserRepository;

/**
 * The owner's monthly goals (sales, waxes, treatments, product per guest) and, for the
 * Home page card, how much is done and what each remaining day needs.
 *
 * Remaining days aren't all equal: each is weighted by stylist hours. A day in a week
 * that's on the schedule uses its scheduled hours (no one scheduled = closed); later
 * days use that weekday's usual worked hours from the last 8 weeks, so a Saturday
 * carries more than a Sunday.
 */
@Service
public class GoalsService {

	static final String KEY_PREFIX = "goals.";
	/** Month-to-date waxes and treatments, e.g. "goals.counts.2026-11". */
	static final String COUNTS_PREFIX = "goals.counts.";
	/** Pulled again after this long, so today's count catches up once it's in. */
	static final int COUNTS_FRESH_MINUTES = 60;

	private static final Logger log = LoggerFactory.getLogger(GoalsService.class);
	/** How far back "usual hours" and guests per hour look. */
	static final int HISTORY_DAYS = 56;
	/** Roles whose scheduled hours bring in sales. */
	static final Set<Role> SELLING = EnumSet.of(Role.STYLIST, Role.LEAD, Role.MANAGER, Role.GENERAL_MANAGER);

	private final AppSettingRepository settings;
	private final DailySalesRepository days;
	private final ShiftRepository shifts;
	private final UserRepository users;
	private final DailySalesService dailySales;
	private final SalonUltimateClient salonUltimate;
	private final ClosedDayService closedDays;
	private final ZoneId zone;

	public GoalsService(AppSettingRepository settings, DailySalesRepository days, ShiftRepository shifts, UserRepository users,
			DailySalesService dailySales, SalonUltimateClient salonUltimate, ClosedDayService closedDays,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.settings = settings;
		this.days = days;
		this.shifts = shifts;
		this.users = users;
		this.dailySales = dailySales;
		this.salonUltimate = salonUltimate;
		this.closedDays = closedDays;
		this.zone = ZoneId.of(zone);
	}

	/** A month's goals. Any can be blank (no goal for it). */
	public record Goals(BigDecimal sales, Integer waxes, Integer treatments, BigDecimal ppg) {
		boolean any() { return sales != null || waxes != null || treatments != null || ppg != null; }
	}

	/** One goal on the card. PPG has no amount left; its perDay is the PPG needed for the rest of the month. */
	public record GoalLine(String key, String label, boolean money, BigDecimal goal, BigDecimal done, BigDecimal left,
		BigDecimal perDay, boolean met) {}

	/**
	 * One remaining day: its weight in hours, whether that came from the schedule, and what it
	 * needs. {@code closedFor}: why the salon is closed that day ("Christmas Day"), or null.
	 */
	public record DayNeed(LocalDate date, BigDecimal hours, boolean scheduled, BigDecimal sales, Integer waxes,
		Integer treatments, String closedFor) {}

	/** {@code countsThrough}: the last day the waxes and treatments include (null while they load). */
	public record GoalsView(String month, String monthLabel, boolean hasGoals, List<GoalLine> lines, List<DayNeed> days,
		int daysLeft, int daysNotIn, LocalDate countsThrough, BigDecimal ppgNeeded) {}

	/** This month's waxes and treatments from one Detailed Service report, the same one the leaderboard and contests read. */
	record MonthCounts(LocalDate through, int waxes, int treatments, LocalDateTime pulledAt) {}

	// ---- owner: setting goals ----

	public Goals get(YearMonth month) {
		return settings.findById(KEY_PREFIX + month).map(s -> parse(s.getValue())).orElse(new Goals(null, null, null, null));
	}

	public Goals set(YearMonth month, Goals g) {
		YearMonth now = YearMonth.now(zone);
		if (month == null || month.isBefore(now.minusMonths(1)) || month.isAfter(now.plusMonths(12))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goals can be set from last month up to a year ahead");
		}
		if (g == null) g = new Goals(null, null, null, null);
		if (negative(g.sales()) || negative(g.ppg()) || (g.waxes() != null && g.waxes() < 0)
				|| (g.treatments() != null && g.treatments() < 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goals can't be negative");
		}
		Goals clean = new Goals(zeroToNull(g.sales()), g.waxes() == null || g.waxes() == 0 ? null : g.waxes(),
			g.treatments() == null || g.treatments() == 0 ? null : g.treatments(), zeroToNull(g.ppg()));
		if (clean.any()) settings.save(new AppSetting(KEY_PREFIX + month, format(clean)));
		else settings.findById(KEY_PREFIX + month).ifPresent(settings::delete);
		return clean;
	}

	// ---- the Home page card ----

	public GoalsView view() {
		dailySales.catchUp();
		LocalDate today = LocalDate.now(zone);
		YearMonth month = YearMonth.from(today);
		if (get(month).any() && countsDue(month, today)) CompletableFuture.runAsync(() -> refreshCounts(month, today));
		return view(month, today);
	}

	/** Every 30 minutes: keep this month's waxes and treatments current while it has goals. */
	@Scheduled(cron = "0 5,35 * * * *", zone = "${app.time-zone:America/Chicago}")
	public void refreshScheduled() {
		LocalDate today = LocalDate.now(zone);
		YearMonth month = YearMonth.from(today);
		if (get(month).any() && countsDue(month, today)) refreshCounts(month, today);
	}

	private boolean countsDue(YearMonth month, LocalDate today) {
		MonthCounts c = counts(month);
		return c == null || !c.through().equals(countsThrough(month, today))
			|| c.pulledAt().isBefore(LocalDateTime.now(zone).minusMinutes(COUNTS_FRESH_MINUTES));
	}

	/** The last day whose numbers are in: today once its pull is saved, otherwise yesterday. */
	LocalDate countsThrough(YearMonth month, LocalDate today) {
		boolean todayIn = days.findById(today).map(d -> d.getStatus() == DailySales.Status.OK).orElse(false);
		LocalDate through = todayIn ? today : today.minusDays(1);
		return through.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : through;
	}

	/** Pull the month so far from Salon Ultimate and save the totals. A blocked or failed pull keeps the last totals. */
	synchronized void refreshCounts(YearMonth month, LocalDate today) {
		if (!salonUltimate.isConfigured()) return;
		LocalDate through = countsThrough(month, today);
		LocalDate first = month.atDay(1);
		if (through.isBefore(first)) {
			saveCounts(month, new MonthCounts(through, 0, 0, LocalDateTime.now(zone)));
			return;
		}
		try {
			String html = salonUltimate.detailedServiceByEmployee(first, through);
			if (html == null) return;
			var types = SalonUltimateReport.parseDetailedService(html);
			if (types.isEmpty() && SalonUltimateReport.blockedBy(html) != null) {
				log.info("Goals counts for {} waiting on an open timecard", month);
				return;
			}
			saveCounts(month, new MonthCounts(through,
				types.values().stream().mapToInt(SalonUltimateReport.ServiceTypes::waxes).sum(),
				types.values().stream().mapToInt(SalonUltimateReport.ServiceTypes::treatments).sum(),
				LocalDateTime.now(zone)));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Goals counts for {} failed", month, e);
		}
	}

	MonthCounts counts(YearMonth month) {
		return settings.findById(COUNTS_PREFIX + month).map(s -> {
			Map<String, String> m = pairs(s.getValue());
			try {
				return new MonthCounts(LocalDate.parse(m.get("through")), Integer.parseInt(m.get("waxes")),
					Integer.parseInt(m.get("treatments")), LocalDateTime.parse(m.get("at")));
			} catch (RuntimeException e) {
				return null;
			}
		}).orElse(null);
	}

	private void saveCounts(YearMonth month, MonthCounts c) {
		settings.save(new AppSetting(COUNTS_PREFIX + month, "through=" + c.through() + ";waxes=" + c.waxes()
			+ ";treatments=" + c.treatments() + ";at=" + c.pulledAt()));
	}

	GoalsView view(YearMonth month, LocalDate today) {
		Goals g = get(month);
		String label = month.getMonth().getDisplayName(TextStyle.FULL, Locale.US);
		LocalDate first = month.atDay(1);
		LocalDate last = month.atEndOfMonth();

		Map<LocalDate, DailySales> saved = days.findByDateBetweenOrderByDateAsc(first, last).stream()
			.collect(Collectors.toMap(DailySales::getDate, Function.identity()));
		// Today counts as left until its numbers are in.
		LocalDate firstLeft = saved.containsKey(today) && saved.get(today).getStatus() == DailySales.Status.OK
			? today.plusDays(1) : today;

		BigDecimal sales = BigDecimal.ZERO;
		BigDecimal retail = BigDecimal.ZERO;
		int clients = 0;
		MonthCounts mc = counts(month);
		int waxes = mc == null ? 0 : mc.waxes();
		int treatments = mc == null ? 0 : mc.treatments();
		int notIn = 0;
		for (LocalDate d = first; d.isBefore(firstLeft) && !d.isAfter(last); d = d.plusDays(1)) {
			DailySales s = saved.get(d);
			if (s == null || s.getStatus() != DailySales.Status.OK) {
				notIn++;
				continue;
			}
			sales = sales.add(s.total());
			retail = retail.add(nz(s.getRetailSales()));
			clients += s.getClients() == null ? 0 : s.getClients();
		}

		// Weights for the days left.
		List<LocalDate> left = new ArrayList<>();
		for (LocalDate d = firstLeft.isBefore(first) ? first : firstLeft; !d.isAfter(last); d = d.plusDays(1)) left.add(d);
		History h = history(today);
		Map<LocalDate, BigDecimal> scheduled = scheduledHours(left);
		// A closed day (checked holiday, or marked on the time off calendar) needs nothing.
		Map<LocalDate, String> closed = left.isEmpty() ? Map.of() : closedDays.closedBetween(left.get(0), last);
		List<BigDecimal> weights = new ArrayList<>();
		for (LocalDate d : left) {
			weights.add(closed.containsKey(d) ? BigDecimal.ZERO
				: scheduled.containsKey(d) ? scheduled.get(d) : h.usualHours(d.getDayOfWeek()));
		}
		BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		int openLeft = (int) weights.stream().filter(w -> w.signum() > 0).count();

		BigDecimal salesLeft = g.sales() == null ? null : g.sales().subtract(sales).max(BigDecimal.ZERO);
		int[] waxesPerDay = g.waxes() == null ? null : apportion(Math.max(0, g.waxes() - waxes), weights);
		int[] treatmentsPerDay = g.treatments() == null ? null : apportion(Math.max(0, g.treatments() - treatments), weights);

		List<DayNeed> needs = new ArrayList<>();
		for (int i = 0; i < left.size(); i++) {
			BigDecimal w = weights.get(i);
			BigDecimal daySales = salesLeft == null || totalWeight.signum() == 0 ? null
				: salesLeft.multiply(w).divide(totalWeight, 0, RoundingMode.HALF_UP);
			needs.add(new DayNeed(left.get(i), w.setScale(1, RoundingMode.HALF_UP), scheduled.containsKey(left.get(i)), daySales,
				waxesPerDay == null ? null : waxesPerDay[i], treatmentsPerDay == null ? null : treatmentsPerDay[i],
				closed.get(left.get(i))));
		}

		// Product per guest: retail ÷ guests. What the rest of the month needs to finish on the goal,
		// with guests left estimated from the usual guests per hour.
		BigDecimal ppgSoFar = clients == 0 ? null : retail.divide(BigDecimal.valueOf(clients), 2, RoundingMode.HALF_UP);
		BigDecimal ppgNeeded = null;
		if (g.ppg() != null) {
			BigDecimal guestsLeft = h.guestsPerHour().multiply(totalWeight);
			if (guestsLeft.signum() > 0) {
				BigDecimal retailNeeded = g.ppg().multiply(guestsLeft.add(BigDecimal.valueOf(clients))).subtract(retail);
				ppgNeeded = retailNeeded.max(BigDecimal.ZERO).divide(guestsLeft, 2, RoundingMode.HALF_UP);
			}
		}

		List<GoalLine> lines = new ArrayList<>();
		if (g.sales() != null) {
			lines.add(new GoalLine("SALES", "Sales", true, g.sales(), sales.setScale(0, RoundingMode.HALF_UP), salesLeft,
				perDay(salesLeft, openLeft, 0), salesLeft.signum() == 0));
		}
		if (g.waxes() != null) lines.add(countLine("WAXES", "Waxes", g.waxes(), waxes, openLeft));
		if (g.treatments() != null) lines.add(countLine("TREATMENTS", "Treatments", g.treatments(), treatments, openLeft));
		if (g.ppg() != null) {
			lines.add(new GoalLine("PPG", "Product per guest", true, g.ppg(), ppgSoFar, null, ppgNeeded,
				ppgSoFar != null && ppgSoFar.compareTo(g.ppg()) >= 0 && (ppgNeeded == null || ppgNeeded.compareTo(g.ppg()) <= 0)));
		}

		return new GoalsView(month.toString(), label, g.any(), lines, needs, openLeft, notIn, mc == null ? null : mc.through(), ppgNeeded);
	}

	private static GoalLine countLine(String key, String label, int goal, int done, int openLeft) {
		BigDecimal left = BigDecimal.valueOf(Math.max(0, goal - done));
		return new GoalLine(key, label, false, BigDecimal.valueOf(goal), BigDecimal.valueOf(done), left,
			perDay(left, openLeft, 1), left.signum() == 0);
	}

	private static BigDecimal perDay(BigDecimal left, int openLeft, int scale) {
		if (left == null || openLeft == 0) return null;
		return left.divide(BigDecimal.valueOf(openLeft), scale, RoundingMode.HALF_UP);
	}

	/** Split a whole number across days by weight, so the days add up exactly (largest remainder). */
	static int[] apportion(int total, List<BigDecimal> weights) {
		int[] out = new int[weights.size()];
		double sum = weights.stream().mapToDouble(BigDecimal::doubleValue).sum();
		if (sum <= 0 || total <= 0) return out;
		double[] rest = new double[out.length];
		int given = 0;
		for (int i = 0; i < out.length; i++) {
			double exact = total * weights.get(i).doubleValue() / sum;
			out[i] = (int) Math.floor(exact);
			rest[i] = exact - out[i];
			given += out[i];
		}
		while (given < total) {
			int best = 0;
			for (int i = 1; i < out.length; i++) if (rest[i] > rest[best]) best = i;
			out[best]++;
			rest[best] = -1;
			given++;
		}
		return out;
	}

	/** Usual worked hours by weekday and guests per hour, from the last 8 weeks of saved days. */
	record History(Map<DayOfWeek, BigDecimal> hoursByDay, BigDecimal overallHours, BigDecimal guestsPerHour) {
		BigDecimal usualHours(DayOfWeek d) {
			return hoursByDay.getOrDefault(d, overallHours);
		}
	}

	History history(LocalDate today) {
		List<DailySales> recent = days.findByDateBetweenOrderByDateAsc(today.minusDays(HISTORY_DAYS), today.minusDays(1))
			.stream().filter(DailySales::wasOpen).filter(d -> d.getFloorHours() != null && d.getFloorHours().signum() > 0).toList();
		Map<DayOfWeek, List<BigDecimal>> byDay = new EnumMap<>(DayOfWeek.class);
		BigDecimal hours = BigDecimal.ZERO;
		int guests = 0;
		for (DailySales d : recent) {
			byDay.computeIfAbsent(d.getDate().getDayOfWeek(), k -> new ArrayList<>()).add(d.getFloorHours());
			hours = hours.add(d.getFloorHours());
			guests += d.getClients() == null ? 0 : d.getClients();
		}
		Map<DayOfWeek, BigDecimal> avg = new EnumMap<>(DayOfWeek.class);
		byDay.forEach((day, list) -> avg.put(day, average(list)));
		// No history at all: every day the same.
		BigDecimal overall = recent.isEmpty() ? BigDecimal.ONE : hours.divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);
		BigDecimal gph = hours.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(guests).divide(hours, 4, RoundingMode.HALF_UP);
		return new History(avg, overall, gph);
	}

	/**
	 * Scheduled stylist hours for days in weeks (Saturday to Friday) that have any shifts.
	 * A day in such a week with no one scheduled is 0 (closed). Days in weeks not built yet are left out.
	 */
	Map<LocalDate, BigDecimal> scheduledHours(List<LocalDate> dates) {
		Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
		if (dates.isEmpty()) return out;
		LocalDate from = weekStart(dates.get(0));
		LocalDate to = dates.get(dates.size() - 1);
		List<Shift> list = shifts.findByDateBetweenOrderByDateAscStartAsc(from, weekStart(to).plusDays(6));
		Set<Long> selling = new HashSet<>();
		for (User u : users.findAll()) if (u.isActive() && SELLING.contains(u.getRoles())) selling.add(u.getUserId());
		Set<LocalDate> builtWeeks = new HashSet<>();
		Map<LocalDate, BigDecimal> hours = new LinkedHashMap<>();
		for (Shift s : list) {
			if (s.isRequestedOff() || s.getStart() == null || s.getEnd() == null) continue;
			builtWeeks.add(weekStart(s.getDate()));
			if (!selling.contains(s.getUserId())) continue;
			BigDecimal h = BigDecimal.valueOf(Duration.between(s.getStart(), s.getEnd()).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
			hours.merge(s.getDate(), h.max(BigDecimal.ZERO), BigDecimal::add);
		}
		for (LocalDate d : dates) {
			if (builtWeeks.contains(weekStart(d))) out.put(d, hours.getOrDefault(d, BigDecimal.ZERO));
		}
		return out;
	}

	/** The Saturday a schedule week starts on. */
	static LocalDate weekStart(LocalDate d) {
		return d.minusDays((d.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7);
	}

	// ---- storage: "sales=40000;waxes=60;treatments=80;ppg=12.5" ----

	private static Map<String, String> pairs(String value) {
		Map<String, String> m = new LinkedHashMap<>();
		for (String part : value == null ? new String[0] : value.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) m.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
		}
		return m;
	}

	static Goals parse(String value) {
		Map<String, String> m = pairs(value);
		return new Goals(decimal(m.get("sales")), whole(m.get("waxes")), whole(m.get("treatments")), decimal(m.get("ppg")));
	}

	static String format(Goals g) {
		List<String> parts = new ArrayList<>();
		if (g.sales() != null) parts.add("sales=" + g.sales().toPlainString());
		if (g.waxes() != null) parts.add("waxes=" + g.waxes());
		if (g.treatments() != null) parts.add("treatments=" + g.treatments());
		if (g.ppg() != null) parts.add("ppg=" + g.ppg().toPlainString());
		return String.join(";", parts);
	}

	private static BigDecimal decimal(String s) {
		try {
			return s == null || s.isBlank() ? null : new BigDecimal(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer whole(String s) {
		try {
			return s == null || s.isBlank() ? null : Integer.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static BigDecimal average(List<BigDecimal> list) {
		return list.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
	}

	private static boolean negative(BigDecimal n) { return n != null && n.signum() < 0; }

	private static BigDecimal zeroToNull(BigDecimal n) { return n == null || n.signum() == 0 ? null : n; }

	private static BigDecimal nz(BigDecimal n) { return n == null ? BigDecimal.ZERO : n; }
}
