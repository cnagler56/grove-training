package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.DailySales;
import com.grove.Domain.DailySales.Status;
import com.grove.Domain.Dtos.DailySalesDay;
import com.grove.Domain.Dtos.DailySalesView;
import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.DailySalesRepository;
import com.grove.Repository.UserRepository;

/**
 * Each day's business, pulled from Salon Ultimate once, 30 minutes after closing:
 * <ul>
 * <li>saved for the owner's Home page (a 30-day chart, month to date, and a projected month)</li>
 * <li>sent to owners as a short summary</li>
 * <li>if a timecard is still open, Salon Ultimate won't give the numbers; whoever the owner's
 *     Settings give open timecard alerts is told who, once. That day is tried again quietly once a day, so the chart can fill in
 *     if someone closes it later.</li>
 * </ul>
 * Days before this started are filled in a few at a time, also without notifications.
 */
@Service
public class DailySalesService {

	private static final Logger log = LoggerFactory.getLogger(DailySalesService.class);

	/** Closing time by day, from the salon's hours (salon.ts). */
	static final Map<DayOfWeek, LocalTime> CLOSING = Map.of(
		DayOfWeek.SUNDAY, LocalTime.of(16, 0),
		DayOfWeek.MONDAY, LocalTime.of(20, 0),
		DayOfWeek.TUESDAY, LocalTime.of(20, 0),
		DayOfWeek.WEDNESDAY, LocalTime.of(20, 0),
		DayOfWeek.THURSDAY, LocalTime.of(20, 0),
		DayOfWeek.FRIDAY, LocalTime.of(18, 0),
		DayOfWeek.SATURDAY, LocalTime.of(17, 0));
	static final int MINUTES_AFTER_CLOSE = 30;
	/** How far back the chart goes, and how far back missing days are filled in. */
	static final int DAYS_SHOWN = 30;
	/** Missing older days pulled per run, so Salon Ultimate isn't hit all at once. */
	static final int BACKFILL_PER_RUN = 3;
	/** A past day that didn't save is tried again after this long. */
	static final int RETRY_HOURS = 24;
	/** Owners who turned off the daily summary notification. */
	static final String SUMMARY_OFF_KEY = "dailySales.summaryOff";

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);

	private final DailySalesRepository days;
	private final UserRepository users;
	private final SalonUltimateClient salonUltimate;
	private final PushService push;
	private final PermissionService perms;
	private final AppSettingRepository settings;
	private final ZoneId zone;

	public DailySalesService(DailySalesRepository days, UserRepository users, SalonUltimateClient salonUltimate,
			PushService push, PermissionService perms, AppSettingRepository settings, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.days = days;
		this.users = users;
		this.salonUltimate = salonUltimate;
		this.push = push;
		this.perms = perms;
		this.settings = settings;
		this.zone = ZoneId.of(zone);
	}

	/** When a day's numbers are pulled: 30 minutes after that day's closing time. */
	public static LocalDateTime pullTime(LocalDate date) {
		return date.atTime(CLOSING.get(date.getDayOfWeek())).plusMinutes(MINUTES_AFTER_CLOSE);
	}

	/** Every 15 minutes: today's pull once its time has come, then a few missing older days. */
	@Scheduled(cron = "0 */15 * * * *", zone = "${app.time-zone:America/Chicago}")
	public void run() {
		if (!salonUltimate.isConfigured()) return;
		LocalDateTime now = LocalDateTime.now(zone);
		LocalDate today = now.toLocalDate();
		try {
			if (!now.isBefore(pullTime(today)) && !days.existsById(today)) pull(today, true);
			backfill(today, now);
		} catch (RuntimeException e) {
			log.warn("Daily sales run failed", e);
		}
	}

	/** In the background, if the server was asleep at the usual time (the owner's Home page asks). */
	public void catchUp() {
		if (salonUltimate.isConfigured()) CompletableFuture.runAsync(this::run);
	}

	/**
	 * The owner's Try again on a day that didn't come in (say a timecard was closed late).
	 * Quiet like the daily retries: the owner pressing it sees the numbers on the card.
	 */
	public DailySalesView retry(LocalDate date) {
		if (!salonUltimate.isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Salon Ultimate sign-in isn't set up on the server");
		}
		LocalDateTime now = LocalDateTime.now(zone);
		LocalDate today = now.toLocalDate();
		if (date == null || date.isAfter(today) || date.isBefore(today.minusDays(DAYS_SHOWN - 1L))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the last " + DAYS_SHOWN + " days can be pulled again");
		}
		if (date.equals(today) && now.isBefore(pullTime(today))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Today's numbers can't be pulled until 30 minutes after closing");
		}
		pull(date, false);
		return view();
	}

	/** Older days that are missing, or didn't save and are due another try. Quiet: no notifications. */
	private void backfill(LocalDate today, LocalDateTime now) {
		int pulled = 0;
		for (int i = 1; i <= DAYS_SHOWN && pulled < BACKFILL_PER_RUN; i++) {
			LocalDate d = today.minusDays(i);
			DailySales saved = days.findById(d).orElse(null);
			boolean due = saved == null
				|| (saved.getStatus() != Status.OK && saved.getAttemptedAt().isBefore(now.minusHours(RETRY_HOURS)))
				// Saved before waxes and treatments were counted: this month's days fill in for goals.
				|| (saved.wasOpen() && saved.getWaxes() == null && YearMonth.from(d).equals(YearMonth.from(today))
					&& saved.getAttemptedAt().isBefore(now.minusHours(1)));
			if (!due) continue;
			pull(d, false);
			pulled++;
		}
	}

	/**
	 * Pull one day and save it. {@code live} is today's after-closing pull: it sends the owners'
	 * summary, or tells managers and up about an open timecard.
	 */
	synchronized DailySales pull(LocalDate date, boolean live) {
		DailySales d = days.findById(date).orElseGet(() -> new DailySales(date));
		d.setAttemptedAt(LocalDateTime.now(zone));
		String html;
		try {
			html = salonUltimate.weekReport(date, date);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return fail(d, "The pull was interrupted.");
		} catch (Exception e) {
			log.warn("Daily sales pull for {} failed", date, e);
			return fail(d, e instanceof SalonUltimateClient.LoginFailed
				? "Salon Ultimate didn't accept the sign-in." : "Couldn't reach Salon Ultimate.");
		}

		String blocked = SalonUltimateReport.blockedBy(html);
		if (blocked != null) {
			d.setStatus(Status.BLOCKED);
			d.setMessage(blocked.length() > 500 ? blocked.substring(0, 500) : blocked);
			if (live && !d.isTimecardAlertSent()) {
				alertManagers(date, blocked);
				d.setTimecardAlertSent(true);
			}
			if (live && !d.isSummarySent()) {
				push.notify(summaryRecipients(), "Today's business: waiting on a timecard",
					"Salon Ultimate won't give " + date.format(DAY) + "'s numbers until this timecard is closed: " + blocked,
					"/home");
				d.setSummarySent(true);
			}
			return days.save(d);
		}

		List<SalonUltimateReport.Row> rows = SalonUltimateReport.parse(html);
		d.setStatus(Status.OK);
		d.setMessage(rows.isEmpty() ? "No one worked; the salon was closed." : null);
		d.setStaffWorked(rows.size());
		d.setServiceSales(sum(rows, SalonUltimateReport.Row::serviceSales));
		d.setNetServiceSales(sum(rows, SalonUltimateReport.Row::netServiceSales));
		d.setRetailSales(sum(rows, SalonUltimateReport.Row::retailSales));
		d.setTips(sum(rows, SalonUltimateReport.Row::tips));
		d.setFloorHours(sum(rows, SalonUltimateReport.Row::floorHours));
		if (!rows.isEmpty()) counts(date, d);
		DailySales saved = days.save(d);
		if (live && !saved.isSummarySent() && saved.wasOpen()) {
			sendSummary(saved);
			saved.setSummarySent(true);
			saved = days.save(saved);
		}
		return saved;
	}

	/** Clients and services from the Stylist Comparison report. A problem here still saves the sales. */
	private void counts(LocalDate date, DailySales d) {
		try {
			String html = salonUltimate.stylistComparison(date, date);
			if (html == null) return;
			var counts = SalonUltimateReport.parseStylistComparison(html).values();
			d.setClients(counts.stream().mapToInt(SalonUltimateReport.Counts::clients).sum());
			d.setServices(counts.stream().mapToInt(SalonUltimateReport.Counts::services).sum());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Stylist Comparison for {} failed; saving the day without clients and services", date, e);
		}
		try {
			String html = salonUltimate.detailedServiceByEmployee(date, date);
			if (html == null) return;
			var types = SalonUltimateReport.parseDetailedService(html).values();
			d.setWaxes(types.stream().mapToInt(SalonUltimateReport.ServiceTypes::waxes).sum());
			d.setTreatments(types.stream().mapToInt(SalonUltimateReport.ServiceTypes::treatments).sum());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Detailed Service for {} failed; saving the day without waxes and treatments", date, e);
		}
	}

	private DailySales fail(DailySales d, String message) {
		d.setStatus(Status.FAILED);
		d.setMessage(message);
		return days.save(d);
	}

	/** Tell everyone the owner's Settings give open timecard alerts (owners always get them). */
	private void alertManagers(LocalDate date, String who) {
		List<Long> managers = users.findAll().stream()
			.filter(u -> perms.has(u, Permission.TIMECARD_ALERTS))
			.map(User::getUserId).toList();
		push.notify(managers, "Open timecard",
			"Still clocked in after closing on " + date.format(DAY) + ": " + who + ". Please close it in Salon Ultimate.",
			"/home");
	}

	private void sendSummary(DailySales d) {
		YearMonth month = YearMonth.from(d.getDate());
		Projection p = projection(days.findByDateBetweenOrderByDateAsc(month.atDay(1), month.atEndOfMonth()), month);
		StringBuilder body = new StringBuilder(money(d.total()) + " in sales: "
			+ money(d.getNetServiceSales()) + " services, " + money(d.getRetailSales()) + " retail");
		if (d.getClients() != null) body.append(", ").append(d.getClients()).append(" clients");
		body.append(". Month so far ").append(money(p.monthToDate())).append(", on pace for ").append(money(p.projected())).append('.');
		push.notify(summaryRecipients(), "Today's business, " + d.getDate().format(DAY), body.toString(), "/home");
	}

	/** Owners who get the daily summary: every active owner, unless they've turned it off in Settings. */
	private List<Long> summaryRecipients() {
		var off = summaryOff();
		return users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles() == Role.OWNER && !off.contains(u.getUserId()))
			.map(User::getUserId).toList();
	}

	/** Owners and whether each gets the daily summary, for Settings. */
	public List<SummaryRecipient> recipients() {
		var off = summaryOff();
		return users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles() == Role.OWNER)
			.sorted(java.util.Comparator.comparing(DailySalesService::name, String.CASE_INSENSITIVE_ORDER))
			.map(u -> new SummaryRecipient(u.getUserId(), name(u), !off.contains(u.getUserId())))
			.toList();
	}

	/** Turn the daily summary on or off for one owner. */
	public List<SummaryRecipient> setRecipient(Long userId, boolean on) {
		User u = userId == null ? null : users.findById(userId).orElse(null);
		if (u == null || u.getRoles() != Role.OWNER) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
				"Only owners get the daily sales summary");
		}
		var off = new java.util.TreeSet<>(summaryOff());
		if (on) off.remove(userId);
		else off.add(userId);
		settings.save(new AppSetting(SUMMARY_OFF_KEY, String.join(",", off.stream().map(String::valueOf).toList())));
		return recipients();
	}

	/** Owners who turned the summary off, kept as "3,7" in app_settings. */
	private java.util.Set<Long> summaryOff() {
		java.util.Set<Long> out = new java.util.HashSet<>();
		settings.findById(SUMMARY_OFF_KEY).ifPresent(s -> {
			for (String part : s.getValue().split(",")) {
				try {
					if (!part.isBlank()) out.add(Long.parseLong(part.trim()));
				} catch (NumberFormatException e) {
					// Not an id: ignore it.
				}
			}
		});
		return out;
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	/** An owner on the Settings list, and whether they get the daily sales summary. */
	public record SummaryRecipient(Long userId, String name, boolean on) {}

	public record SummaryRecipientInput(Long userId, boolean on) {}

	/**
	 * Month to date, the average for days the salon was open, and that average across every day
	 * the salon opens this month. Days not yet here count as open (the salon opens every day);
	 * a past day that shows no one worked counts as closed.
	 */
	public record Projection(BigDecimal monthToDate, int openDaysSoFar, BigDecimal averagePerOpenDay, int openDaysInMonth,
		BigDecimal projected) {}

	static Projection projection(List<DailySales> monthDays, YearMonth month) {
		BigDecimal total = BigDecimal.ZERO;
		int open = 0;
		int closed = 0;
		for (DailySales d : monthDays) {
			if (d.wasOpen()) {
				total = total.add(d.total());
				open++;
			} else if (d.getStatus() == Status.OK) {
				closed++;
			}
		}
		int openInMonth = month.lengthOfMonth() - closed;
		BigDecimal average = open == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(open), 2, RoundingMode.HALF_UP);
		BigDecimal projected = average.multiply(BigDecimal.valueOf(openInMonth)).setScale(2, RoundingMode.HALF_UP);
		return new Projection(total.setScale(2, RoundingMode.HALF_UP), open, average, openInMonth, projected);
	}

	/** The owner's Home page card. */
	public DailySalesView view() {
		catchUp();
		LocalDate today = LocalDate.now(zone);
		List<DailySales> recent = days.findByDateBetweenOrderByDateAsc(today.minusDays(DAYS_SHOWN - 1L), today);
		YearMonth month = YearMonth.from(today);
		Projection p = projection(days.findByDateBetweenOrderByDateAsc(month.atDay(1), month.atEndOfMonth()), month);
		DailySales latest = recent.stream().filter(DailySales::wasOpen).reduce((a, b) -> b).orElse(null);
		DailySales todays = recent.stream().filter(d -> d.getDate().equals(today)).findFirst().orElse(null);
		return new DailySalesView(
			salonUltimate.isConfigured(),
			pullTime(today),
			recent.stream().map(DailySalesService::day).toList(),
			latest == null ? null : day(latest),
			todays == null ? null : day(todays),
			p.monthToDate(), p.openDaysSoFar(), p.averagePerOpenDay(), p.openDaysInMonth(), p.projected());
	}

	private static DailySalesDay day(DailySales d) {
		return new DailySalesDay(d.getDate(), d.getStatus().name(), d.getMessage(), d.wasOpen(),
			d.getStatus() == Status.OK ? d.total() : null, d.getNetServiceSales(), d.getRetailSales(), d.getTips(),
			d.getClients(), d.getServices(), d.getStaffWorked());
	}

	private static BigDecimal sum(List<SalonUltimateReport.Row> rows, java.util.function.Function<SalonUltimateReport.Row, BigDecimal> f) {
		return rows.stream().map(f).map(n -> n == null ? BigDecimal.ZERO : n).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static String money(BigDecimal n) {
		NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
		f.setMaximumFractionDigits(0);
		f.setRoundingMode(RoundingMode.HALF_UP);
		return f.format(n == null ? BigDecimal.ZERO : n);
	}
}
