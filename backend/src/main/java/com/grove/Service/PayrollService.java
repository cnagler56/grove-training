package com.grove.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.PayPeriod;
import com.grove.Domain.Dtos.PayPeriods;
import com.grove.Domain.Dtos.PayrollPayRow;
import com.grove.Domain.Dtos.PayrollPayWeek;
import com.grove.Domain.Dtos.PayrollReport;
import com.grove.Domain.Dtos.PayrollWeek;
import com.grove.Domain.Dtos.StatsImportView;
import com.grove.Domain.Dtos.PayrollEmployee;
import com.grove.Domain.Dtos.PayrollNoteInput;
import com.grove.Domain.Dtos.PayrollNoteView;
import com.grove.Domain.Dtos.PayrollPto;
import com.grove.Domain.Dtos.PayrollSummary;
import com.grove.Domain.Dtos.Person;
import com.grove.Domain.PayrollNote;
import com.grove.Domain.PayrollNote.Kind;
import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Domain.VacationPayRequest;
import com.grove.Domain.VacationPayRequest.Status;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Repository.PayrollNoteRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.VacationPayRequestRepository;

/**
 * Pay periods, payroll notes (deductions, additions, comments), and the Payroll
 * tab that puts the notes and paid time off for one check in one place.
 *
 * <p>Periods are keyed by check date, the same dates Paid Time Off uses. Each
 * check pays for the two Saturday-to-Friday weeks that ended
 * {@link #DAYS_AFTER_PERIOD} days before it.
 */
@Service
public class PayrollService {

	/** A Friday check pays for the period that ended the Friday before. */
	static final int DAYS_AFTER_PERIOD = 7;
	/** How many past periods the picker goes back. */
	static final int PAST_PERIODS = 12;
	/** How many periods after the default one the picker offers, for notes entered early. */
	static final int UPCOMING_PERIODS = 3;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");
	private static final int MAX_NOTE = 500;

	private final PayrollNoteRepository notes;
	private final VacationPayRequestRepository vacationPay;
	private final UserRepository users;
	private final PermissionService perms;
	private final PushService push;
	private final PayService pay;
	private final StatsService statsService;
	private final WeeklyStatRepository stats;
	private final ZoneId zone;

	public PayrollService(PayrollNoteRepository notes, VacationPayRequestRepository vacationPay, UserRepository users,
			PermissionService perms, PushService push, PayService pay, StatsService statsService, WeeklyStatRepository stats,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.notes = notes;
		this.vacationPay = vacationPay;
		this.users = users;
		this.perms = perms;
		this.push = push;
		this.pay = pay;
		this.statsService = statsService;
		this.stats = stats;
		this.zone = ZoneId.of(zone);
	}

	public static PayPeriod period(LocalDate checkDate) {
		LocalDate workEnd = checkDate.minusDays(DAYS_AFTER_PERIOD);
		return new PayPeriod(checkDate, workEnd.minusDays(13), workEnd);
	}

	public static boolean isCheckDate(LocalDate d) {
		long days = ChronoUnit.DAYS.between(VacationPayService.FIRST_CHECK_DATE, d);
		return Math.floorMod(days, VacationPayService.DAYS_BETWEEN_CHECKS) == 0;
	}

	/** The latest period whose two work weeks are over by {@code today}: the one being paid next. */
	public static LocalDate defaultCheckDate(LocalDate today) {
		// Work ends on checkDate - 7, so a period is over once today is after that Friday.
		LocalDate latest = today.plusDays(DAYS_AFTER_PERIOD - 1);
		long days = ChronoUnit.DAYS.between(VacationPayService.FIRST_CHECK_DATE, latest);
		return latest.minusDays(Math.floorMod(days, VacationPayService.DAYS_BETWEEN_CHECKS));
	}

	public static PayPeriods periodsAround(LocalDate today) {
		LocalDate def = defaultCheckDate(today);
		List<PayPeriod> list = new ArrayList<>();
		for (int i = UPCOMING_PERIODS; i >= -PAST_PERIODS; i--) {
			list.add(period(def.plusDays((long) i * VacationPayService.DAYS_BETWEEN_CHECKS)));
		}
		return new PayPeriods(list, def);
	}

	public PayPeriods periods() {
		return periodsAround(LocalDate.now(zone));
	}

	/** Who notes can be written for: everyone active on the schedule, by name. */
	public List<Person> employees() {
		return users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles().isScheduled())
			.sorted(Comparator.comparing(PayrollService::name, String.CASE_INSENSITIVE_ORDER))
			.map(u -> new Person(u.getUserId(), name(u), u.getRoles()))
			.toList();
	}

	public List<PayrollNoteView> notesFor(LocalDate checkDate) {
		requireCheckDate(checkDate);
		Map<Long, String> names = names();
		return notes.findByCheckDateOrderByCreatedAtAsc(checkDate).stream()
			.map(n -> view(n, names))
			.sorted(Comparator.comparing(PayrollNoteView::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public PayrollNoteView add(User author, PayrollNoteInput input) {
		PayrollNote n = new PayrollNote();
		apply(n, input);
		n.setCreatedBy(author.getUserId());
		n.setCreatedAt(LocalDateTime.now(zone));
		PayrollNote saved = notes.save(n);
		Map<Long, String> names = names();
		tellPayroll(author, "New payroll note", names, saved);
		return view(saved, names);
	}

	/** A change after it was acknowledged sends it back to be acknowledged again. Finished notes are locked. */
	public PayrollNoteView update(User editor, Long id, PayrollNoteInput input) {
		PayrollNote n = find(id);
		requireOpen(n);
		apply(n, input);
		n.setUpdatedBy(editor.getUserId());
		n.setUpdatedAt(LocalDateTime.now(zone));
		boolean wasAcknowledged = n.getStatus() == PayrollNote.Status.ACKNOWLEDGED;
		n.setStatus(PayrollNote.Status.NEW);
		n.setAcknowledgedBy(null);
		n.setAcknowledgedAt(null);
		PayrollNote saved = notes.save(n);
		Map<Long, String> names = names();
		if (wasAcknowledged) tellPayroll(editor, "Payroll note changed", names, saved);
		return view(saved, names);
	}

	public void delete(Long id) {
		PayrollNote n = find(id);
		requireOpen(n);
		notes.delete(n);
	}

	/** The person doing payroll lets whoever added the note know they've seen it. */
	public PayrollNoteView acknowledge(User approver, Long id) {
		PayrollNote n = find(id);
		if (n.getStatus() != PayrollNote.Status.NEW) throw bad("This note was already acknowledged");
		n.setStatus(PayrollNote.Status.ACKNOWLEDGED);
		n.setAcknowledgedBy(approver.getUserId());
		n.setAcknowledgedAt(LocalDateTime.now(zone));
		PayrollNote saved = notes.save(n);
		Map<Long, String> names = names();
		tellAuthor(approver, saved, names, "Payroll note acknowledged", " acknowledged your payroll note: ");
		return view(saved, names);
	}

	/** Entered into payroll: the note is closed. */
	public PayrollNoteView finish(User approver, Long id) {
		PayrollNote n = find(id);
		if (n.getStatus() == PayrollNote.Status.NEW) throw bad("Acknowledge this note first");
		if (n.getStatus() == PayrollNote.Status.FINISHED) throw bad("This note is already finished");
		n.setStatus(PayrollNote.Status.FINISHED);
		n.setFinishedBy(approver.getUserId());
		n.setFinishedAt(LocalDateTime.now(zone));
		PayrollNote saved = notes.save(n);
		Map<Long, String> names = names();
		tellAuthor(approver, saved, names, "Payroll note finished", " entered your payroll note into payroll: ");
		return view(saved, names);
	}

	/** Undo Finished, for a note closed by mistake. It goes back to acknowledged. */
	public PayrollNoteView reopen(Long id) {
		PayrollNote n = find(id);
		if (n.getStatus() != PayrollNote.Status.FINISHED) throw bad("This note isn't finished");
		n.setStatus(PayrollNote.Status.ACKNOWLEDGED);
		n.setFinishedBy(null);
		n.setFinishedAt(null);
		return view(notes.save(n), names());
	}

	/** Approved paid time off has been entered into payroll: tell the employee and close it. */
	public void finishPto(User approver, Long id) {
		VacationPayRequest r = vacationPay.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		if (r.getStatus() != Status.APPROVED) throw bad("Only approved paid time off can be marked Finished");
		if (r.isFinished()) throw bad("This is already finished");
		r.setFinishedBy(approver.getUserId());
		r.setFinishedAt(LocalDateTime.now(zone));
		vacationPay.save(r);
		String hours = r.getHours().stripTrailingZeros().toPlainString();
		push.notify(List.of(r.getUserId()), "Paid time off entered into payroll",
			name(approver) + " entered your " + hours + " hours of paid time off into payroll for the "
				+ r.getCheckDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + " check.",
			"/paid-time-off");
	}

	/** Undo Finished on paid time off marked by mistake. */
	public void reopenPto(Long id) {
		VacationPayRequest r = vacationPay.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		if (!r.isFinished()) throw bad("This isn't finished");
		r.setFinishedBy(null);
		r.setFinishedAt(null);
		vacationPay.save(r);
	}

	private void requireOpen(PayrollNote n) {
		if (n.getStatus() == PayrollNote.Status.FINISHED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This note is already entered into payroll. Ask to have it reopened first.");
		}
	}

	/** Tell everyone with the Payroll tab (but not the person who did it) about a note to look at. */
	private void tellPayroll(User actor, String title, Map<Long, String> names, PayrollNote n) {
		var roles = perms.roles(Permission.VIEW_PAYROLL);
		List<Long> to = users.findAll().stream()
			.filter(u -> u.isActive() && roles.contains(u.getRoles()) && !u.getUserId().equals(actor.getUserId()))
			.map(User::getUserId).toList();
		push.notify(to, title, name(actor) + ": " + describe(n, names), "/payroll");
	}

	private void tellAuthor(User actor, PayrollNote n, Map<Long, String> names, String title, String verb) {
		if (n.getCreatedBy() == null || n.getCreatedBy().equals(actor.getUserId())) return;
		push.notify(List.of(n.getCreatedBy()), title, name(actor) + verb + describe(n, names), "/payroll-notes");
	}

	/** "Ava Stone, deduction $25.50 (Uniform shirt)". */
	private static String describe(PayrollNote n, Map<Long, String> names) {
		String who = names.getOrDefault(n.getUserId(), "Former employee");
		String what = n.getKind().name().toLowerCase() + (n.getAmount() == null ? "" : " $" + n.getAmount().setScale(2).toPlainString());
		return who + ", " + what + (n.getNote() == null ? "" : " (" + n.getNote() + ")");
	}

	/**
	 * The Payroll tab for one check: each employee with approved paid time off
	 * hours and their notes, plus pending requests so nothing is missed.
	 */
	public PayrollSummary summary(LocalDate checkDate) {
		requireCheckDate(checkDate);
		Map<Long, String> names = names();
		Map<Long, Row> rows = new LinkedHashMap<>();
		int pending = 0;
		int openPto = 0;

		for (VacationPayRequest r : vacationPay.findByCheckDateAndStatusIn(checkDate, List.of(Status.APPROVED, Status.PENDING))) {
			Row row = rows.computeIfAbsent(r.getUserId(), Row::new);
			row.requests.add(new PayrollPto(r.getId(), r.getHours(), r.getStatus().name(),
				r.getFinishedBy() == null ? null : names.get(r.getFinishedBy()), r.getFinishedAt()));
			if (r.getStatus() == Status.APPROVED) {
				row.pto = row.pto.add(r.getHours());
				if (!r.isFinished()) openPto++;
			} else {
				row.pendingPto = row.pendingPto.add(r.getHours());
				pending++;
			}
		}
		for (PayrollNote n : notes.findByCheckDateOrderByCreatedAtAsc(checkDate)) {
			Row row = rows.computeIfAbsent(n.getUserId(), Row::new);
			row.notes.add(view(n, names));
			if (n.getKind() == Kind.ADDITION) row.additions = row.additions.add(n.getAmount());
			if (n.getKind() == Kind.DEDUCTION) row.deductions = row.deductions.add(n.getAmount());
		}

		List<PayrollEmployee> employees = rows.values().stream()
			.map(r -> new PayrollEmployee(r.userId, names.getOrDefault(r.userId, "Former employee"),
				r.pto, r.pendingPto, List.copyOf(r.requests), r.additions, r.deductions, List.copyOf(r.notes)))
			.sorted(Comparator.comparing(PayrollEmployee::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new PayrollSummary(period(checkDate), employees,
			employees.stream().map(PayrollEmployee::ptoHours).reduce(BigDecimal.ZERO, BigDecimal::add),
			employees.stream().map(PayrollEmployee::additions).reduce(BigDecimal.ZERO, BigDecimal::add),
			employees.stream().map(PayrollEmployee::deductions).reduce(BigDecimal.ZERO, BigDecimal::add),
			pending,
			(int) employees.stream().flatMap(e -> e.notes().stream()).filter(v -> !"FINISHED".equals(v.status())).count(),
			openPto);
	}

	/**
	 * Hours, tips, rate, and commission for everyone in the check's two weeks, figured
	 * like the payroll skill's ADP report, from the weeks the site pulls every Saturday.
	 */
	public PayrollReport report(LocalDate checkDate) {
		requireCheckDate(checkDate);
		PayPeriod p = period(checkDate);
		LocalDate week1 = p.workStart();
		LocalDate week2 = p.workStart().plusWeeks(1);

		Map<String, WeeklyStat> first = byName(stats.findByWeekStart(week1));
		Map<String, WeeklyStat> second = byName(stats.findByWeekStart(week2));
		java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		first.values().forEach(s -> names.add(s.getSuName()));
		second.values().forEach(s -> names.add(s.getSuName()));

		List<PayrollPayRow> rows = names.stream()
			.map(n -> {
				var rates = pay.rates(n);
				WeeklyStat a = first.get(n.toLowerCase());
				WeeklyStat b = second.get(n.toLowerCase());
				Role role = pay.roleOf(n);
				String problem = role == null
					? "Not linked to a site account, so paid as a stylist. Link them on the Team page."
					: null;
				return new PayrollPayRow(n, PayService.lastFirst(n), role, problem,
					a == null ? null : payWeek(a, rates.apply(a)), b == null ? null : payWeek(b, rates.apply(b)));
			})
			.sorted(Comparator.comparing(PayrollPayRow::name, String.CASE_INSENSITIVE_ORDER))
			.toList();

		List<PayrollPayWeek> all = rows.stream()
			.flatMap(r -> java.util.stream.Stream.of(r.week1(), r.week2()))
			.filter(Objects::nonNull).toList();
		return new PayrollReport(pay.ratesLoaded(), statsService.isConfigured(),
			List.of(new PayrollWeek(week1, statsService.importStatus(week1)), new PayrollWeek(week2, statsService.importStatus(week2))),
			rows,
			sum(all, PayrollPayWeek::floorHours), sum(all, PayrollPayWeek::offFloorHours),
			sum(all, PayrollPayWeek::tips), sum(all, PayrollPayWeek::commission));
	}

	/** Pull (or re-pull) one of the period's weeks from Salon Ultimate now. */
	public StatsImportView pullWeek(LocalDate weekStart) {
		return statsService.importWeek(weekStart);
	}

	private static Map<String, WeeklyStat> byName(List<WeeklyStat> week) {
		Map<String, WeeklyStat> out = new HashMap<>();
		week.forEach(s -> out.put(s.getSuName().toLowerCase(), s));
		return out;
	}

	private static PayrollPayWeek payWeek(WeeklyStat s, BigDecimal rate) {
		return new PayrollPayWeek(rate, PayService.pph(s), orZero(s.getFloorHours()), orZero(s.getOffFloorHours()),
			orZero(s.getTips()), orZero(s.getRetailSales()), orZero(s.getProductPerGuest()), PayService.commission(s));
	}

	private static BigDecimal sum(List<PayrollPayWeek> weeks, java.util.function.Function<PayrollPayWeek, BigDecimal> f) {
		return weeks.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal orZero(BigDecimal n) {
		return n == null ? BigDecimal.ZERO : n;
	}

	private static final class Row {
		final Long userId;
		BigDecimal pto = BigDecimal.ZERO;
		BigDecimal pendingPto = BigDecimal.ZERO;
		BigDecimal additions = BigDecimal.ZERO;
		BigDecimal deductions = BigDecimal.ZERO;
		final List<PayrollNoteView> notes = new ArrayList<>();
		final List<PayrollPto> requests = new ArrayList<>();

		Row(Long userId) { this.userId = userId; }
	}

	private void apply(PayrollNote n, PayrollNoteInput input) {
		if (input == null) throw bad("Fill in the note");
		User employee = input.userId() == null ? null : users.findById(input.userId()).orElse(null);
		if (employee == null || !employee.getRoles().isScheduled()) throw bad("Pick an employee");
		requireCheckDate(input.checkDate());

		Kind kind;
		try {
			kind = Kind.valueOf(Objects.requireNonNullElse(input.kind(), ""));
		} catch (IllegalArgumentException e) {
			throw bad("Pick deduction, addition, or comment");
		}
		String note = clean(input.note());
		BigDecimal amount = input.amount();
		if (kind == Kind.COMMENT) {
			if (note == null) throw bad("Write the comment");
			amount = null;
		} else {
			if (amount == null || amount.signum() <= 0) throw bad("Enter an amount more than $0");
			if (amount.compareTo(MAX_AMOUNT) > 0) throw bad("That amount is too large");
			if (amount.stripTrailingZeros().scale() > 2) throw bad("Amounts can have at most two decimal places (cents)");
		}

		n.setUserId(employee.getUserId());
		n.setCheckDate(input.checkDate());
		n.setKind(kind);
		n.setAmount(amount);
		n.setNote(note);
	}

	private void requireCheckDate(LocalDate d) {
		if (d == null || !isCheckDate(d)) throw bad("Pick a pay period");
	}

	private PayrollNote find(Long id) {
		return notes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
	}

	private Map<Long, String> names() {
		Map<Long, String> names = new HashMap<>();
		users.findAll().forEach(u -> names.put(u.getUserId(), name(u)));
		return names;
	}

	private static PayrollNoteView view(PayrollNote n, Map<Long, String> names) {
		return new PayrollNoteView(n.getId(), n.getUserId(), names.getOrDefault(n.getUserId(), "Former employee"),
			n.getCheckDate(), n.getKind().name(), n.getAmount(), n.getNote(),
			n.getCreatedBy() == null ? null : names.get(n.getCreatedBy()), n.getCreatedAt(),
			n.getUpdatedBy() == null ? null : names.get(n.getUpdatedBy()), n.getUpdatedAt(),
			n.getStatus().name(),
			n.getAcknowledgedBy() == null ? null : names.get(n.getAcknowledgedBy()), n.getAcknowledgedAt(),
			n.getFinishedBy() == null ? null : names.get(n.getFinishedBy()), n.getFinishedAt());
	}

	private static String clean(String s) {
		if (s == null || s.isBlank()) return null;
		String t = s.trim();
		return t.length() <= MAX_NOTE ? t : t.substring(0, MAX_NOTE);
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
