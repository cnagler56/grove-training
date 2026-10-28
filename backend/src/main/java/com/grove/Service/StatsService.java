package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.MyStats;
import com.grove.Domain.Dtos.SalonUltimateName;
import com.grove.Domain.Dtos.StatLine;
import com.grove.Domain.Dtos.StatsImportView;
import com.grove.Domain.Dtos.TeamStatRow;
import com.grove.Domain.Dtos.TeamStats;
import com.grove.Domain.StatsImport;
import com.grove.Domain.StatsImport.Status;
import com.grove.Domain.User;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.StatsImportRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.WeeklyStatRepository;

/**
 * Each employee's weekly numbers from Salon Ultimate. Every Saturday morning
 * (salon time) the week that just ended is pulled and saved; weeks are kept so
 * they can be compared. Staff see only their own weeks; managers and up see
 * everyone's (the leaderboards).
 *
 * If the server was asleep on Saturday, the first visit to a stats page
 * afterwards starts the pull in the background instead.
 */
@Service
public class StatsService {

	private static final Logger log = LoggerFactory.getLogger(StatsService.class);
	/** Don't retry a failed or blocked week on page visits more often than this. */
	private static final int RETRY_MINUTES = 60;

	private final WeeklyStatRepository stats;
	private final StatsImportRepository imports;
	private final UserRepository users;
	private final SalonUltimateClient salonUltimate;
	private final PayService pay;
	private final ZoneId zone;

	public StatsService(WeeklyStatRepository stats, StatsImportRepository imports, UserRepository users,
			SalonUltimateClient salonUltimate, PayService pay, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.stats = stats;
		this.imports = imports;
		this.users = users;
		this.salonUltimate = salonUltimate;
		this.pay = pay;
		this.zone = ZoneId.of(zone);
	}

	/** The Saturday that starts the most recent week that has fully ended. */
	public LocalDate latestCompleteWeek() {
		return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).minusWeeks(1);
	}

	/** Saturday mornings, with retries in case a timecard was still open. */
	@Scheduled(cron = "0 0 6,10,14,18 * * SAT", zone = "${app.time-zone:America/Chicago}")
	public void pullLastWeek() {
		LocalDate week = latestCompleteWeek();
		if (isDone(week) || !salonUltimate.isConfigured()) return;
		StatsImportView result = importWeek(week);
		log.info("Weekly stats for {}: {} {}", week, result.status(), result.message() == null ? "" : result.message());
	}

	/** Start last week's pull in the background if it's missing and wasn't just tried. */
	public void catchUp() {
		LocalDate week = latestCompleteWeek();
		if (isDone(week) || !salonUltimate.isConfigured()) return;
		StatsImport last = imports.findById(week).orElse(null);
		if (last != null && last.getAttemptedAt().isAfter(LocalDateTime.now(zone).minusMinutes(RETRY_MINUTES))) return;
		CompletableFuture.runAsync(() -> importWeek(week));
	}

	private boolean isDone(LocalDate week) {
		return imports.findById(week).map(i -> i.getStatus() == Status.OK).orElse(false);
	}

	/**
	 * Pull one finished week from Salon Ultimate and replace whatever was saved
	 * for it. One pull at a time.
	 */
	public synchronized StatsImportView importWeek(LocalDate weekStart) {
		if (weekStart.getDayOfWeek() != DayOfWeek.SATURDAY) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weeks start on a Saturday");
		}
		if (weekStart.isAfter(latestCompleteWeek())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That week isn't over yet");
		}
		LocalDateTime now = LocalDateTime.now(zone);
		if (!salonUltimate.isConfigured()) {
			return record(new StatsImport(weekStart, Status.NOT_CONFIGURED,
				"Salon Ultimate sign-in isn't set up on the server (SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD).",
				null, now));
		}
		String html;
		try {
			html = salonUltimate.weekReport(weekStart, weekStart.plusDays(6));
		} catch (SalonUltimateClient.LoginFailed e) {
			return record(new StatsImport(weekStart, Status.FAILED,
				"Salon Ultimate didn't accept the sign-in. Check SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD.", null, now));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return record(new StatsImport(weekStart, Status.FAILED, "The pull was interrupted. Try again.", null, now));
		} catch (Exception e) {
			log.warn("Salon Ultimate pull for {} failed", weekStart, e);
			return record(new StatsImport(weekStart, Status.FAILED, "Couldn't reach Salon Ultimate: " + e.getMessage(), null, now));
		}

		List<SalonUltimateReport.Row> rows = SalonUltimateReport.parse(html);
		if (rows.isEmpty()) {
			String blocked = SalonUltimateReport.blockedBy(html);
			return record(blocked != null
				? new StatsImport(weekStart, Status.BLOCKED,
					"Salon Ultimate won't run the report until these timecards are closed: " + blocked, null, now)
				: new StatsImport(weekStart, Status.FAILED,
					"Salon Ultimate's report had no one in it. Its layout may have changed.", null, now));
		}

		Map<String, SalonUltimateReport.Counts> counts = serviceCounts(weekStart);
		stats.deleteWeek(weekStart);
		stats.saveAll(rows.stream().map(r -> toEntity(weekStart, r, counts.get(r.name().toLowerCase()), now)).toList());
		linkByName(rows);
		return record(new StatsImport(weekStart, Status.OK, null, rows.size(), now));
	}

	/**
	 * Each stylist's services and clients from the Stylist Comparison report. A
	 * problem here doesn't stop the week saving; those two numbers are just left out.
	 */
	private Map<String, SalonUltimateReport.Counts> serviceCounts(LocalDate weekStart) {
		try {
			String html = salonUltimate.stylistComparison(weekStart, weekStart.plusDays(6));
			return html == null ? Map.of() : SalonUltimateReport.parseStylistComparison(html);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Map.of();
		} catch (Exception e) {
			log.warn("Stylist Comparison for {} failed; saving the week without services and clients", weekStart, e);
			return Map.of();
		}
	}

	/** The viewer's own weeks, newest first, with each week's pay rate (only ever shown to them). */
	public MyStats mine(User viewer) {
		catchUp();
		String name = viewer.getSalonUltimateName();
		if (name == null || name.isBlank()) return new MyStats(false, null, List.of());
		var rates = pay.rates(name);
		return new MyStats(true, name, stats.findBySuNameIgnoreCaseOrderByWeekStartDesc(name).stream()
			.map(s -> ownLine(s, rates.apply(s))).toList());
	}

	/** Everyone's numbers for one week (latest finished week by default). */
	public TeamStats team(LocalDate week) {
		catchUp();
		LocalDate latest = latestCompleteWeek();
		LocalDate shown = week == null ? latest : week.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
		Map<String, User> linked = linkedUsers();
		List<TeamStatRow> rows = stats.findByWeekStart(shown).stream()
			.map(s -> {
				User u = linked.get(s.getSuName().toLowerCase());
				return new TeamStatRow(s.getSuName(), u == null ? null : u.getUserId(), u == null ? s.getSuName() : name(u), line(s, null));
			})
			.sorted(Comparator.comparing((TeamStatRow r) -> r.stats().serviceSales()).reversed())
			.toList();
		return new TeamStats(shown, latest, stats.weeks(), salonUltimate.isConfigured(),
			imports.findById(shown).map(StatsService::view).orElse(null), rows);
	}

	/** Every name Salon Ultimate has reported and who it's linked to (for the Team page). */
	public List<SalonUltimateName> names() {
		Map<String, User> linked = linkedUsers();
		return stats.names().stream()
			.map(n -> {
				User u = linked.get(n.toLowerCase());
				return new SalonUltimateName(n, u == null ? null : u.getUserId(), u == null ? null : name(u));
			})
			.toList();
	}

	/** Link (or with a blank name, unlink) someone's account to a Salon Ultimate name. */
	public void link(User user, String suName) {
		String name = suName == null || suName.isBlank() ? null : suName.trim();
		if (name != null) {
			User other = linkedUsers().get(name.toLowerCase());
			if (other != null && !other.getUserId().equals(user.getUserId())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, name + " is already linked to " + name(other));
			}
		}
		user.setSalonUltimateName(name);
		users.save(user);
	}

	/** Link accounts whose site name matches a Salon Ultimate name exactly, if they aren't linked yet. */
	private void linkByName(List<SalonUltimateReport.Row> rows) {
		Map<String, User> taken = linkedUsers();
		Map<String, List<User>> bySiteName = users.findAll().stream()
			.filter(u -> u.getSalonUltimateName() == null && u.getRoles().isStaff())
			.collect(Collectors.groupingBy(u -> name(u).toLowerCase()));
		for (SalonUltimateReport.Row r : rows) {
			String key = r.name().toLowerCase();
			List<User> matches = bySiteName.getOrDefault(key, List.of());
			if (matches.size() == 1 && !taken.containsKey(key)) {
				User u = matches.get(0);
				u.setSalonUltimateName(r.name());
				users.save(u);
				taken.put(key, u);
			}
		}
	}

	private Map<String, User> linkedUsers() {
		Map<String, User> out = new HashMap<>();
		users.findAll().stream()
			.filter(u -> u.getSalonUltimateName() != null)
			.forEach(u -> out.putIfAbsent(u.getSalonUltimateName().toLowerCase(), u));
		return out;
	}

	private StatsImportView record(StatsImport i) {
		return view(imports.save(i));
	}

	private static StatsImportView view(StatsImport i) {
		return new StatsImportView(i.getWeekStart(), i.getStatus().name(), i.getMessage(), i.getRowCount(), i.getAttemptedAt());
	}

	private static WeeklyStat toEntity(LocalDate week, SalonUltimateReport.Row r, SalonUltimateReport.Counts counts, LocalDateTime now) {
		WeeklyStat s = new WeeklyStat();
		s.setWeekStart(week);
		s.setSuName(r.name());
		s.setSuPayrollId(r.payrollId());
		s.setFloorHours(r.floorHours());
		s.setOffFloorHours(r.offFloorHours());
		s.setTips(r.tips());
		s.setServiceSales(r.serviceSales());
		s.setNetServiceSales(r.netServiceSales());
		s.setRetailSales(r.retailSales());
		s.setProductPerGuest(r.productPerGuest());
		if (counts != null) {
			s.setServices(counts.services());
			s.setClients(counts.clients());
		}
		s.setImportedAt(now);
		return s;
	}

	/** One week on the employee's own page: adds their pay rate, tips, and per-hour pay. */
	static StatLine ownLine(WeeklyStat s, BigDecimal payRate) {
		StatLine base = line(s, null);
		BigDecimal hours = base.floorHours();
		BigDecimal tips = s.getTips();
		BigDecimal tipsPerHour = tips != null && hours.signum() > 0 ? tips.divide(hours, 2, RoundingMode.HALF_UP) : null;
		BigDecimal total = payRate != null && tipsPerHour != null ? payRate.add(tipsPerHour) : null;
		return new StatLine(base.weekStart(), base.serviceSales(), base.netServiceSales(), base.retailSales(),
			base.productPerGuest(), hours, base.offFloorHours(), base.serviceSalesPerHour(), base.pph(),
			payRate, tips, tipsPerHour, total, base.services(), base.clients());
	}

	/** One week's numbers as managers see them: no pay or tips. */
	static StatLine line(WeeklyStat s, BigDecimal payRate) {
		BigDecimal hours = zeroIfNull(s.getFloorHours());
		BigDecimal sales = zeroIfNull(s.getServiceSales());
		BigDecimal perHour = hours.signum() > 0 ? sales.divide(hours, 2, RoundingMode.HALF_UP) : null;
		return new StatLine(s.getWeekStart(), sales, zeroIfNull(s.getNetServiceSales()), zeroIfNull(s.getRetailSales()),
			zeroIfNull(s.getProductPerGuest()), hours, zeroIfNull(s.getOffFloorHours()), perHour, PayService.pph(s), payRate,
			null, null, null, s.getServices(), s.getClients());
	}

	private static BigDecimal zeroIfNull(BigDecimal n) {
		return n == null ? BigDecimal.ZERO : n;
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}
}
