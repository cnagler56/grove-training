package com.grove.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.TimeOffInput;
import com.grove.Domain.Dtos.TimeOffQueue;
import com.grove.Domain.Dtos.TimeOffView;
import com.grove.Domain.Permission;
import com.grove.Domain.Shift;
import com.grove.Domain.BlackoutDate;
import com.grove.Domain.CalendarNote;
import com.grove.Domain.Dtos.DayNote;
import com.grove.Domain.Dtos.BlackoutView;
import com.grove.Domain.Dtos.TimeOffCalendar;
import com.grove.Domain.TimeOffRequest;
import com.grove.Domain.TimeOffRequest.Status;
import com.grove.Domain.User;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.BlackoutDateRepository;
import com.grove.Repository.CalendarNoteRepository;
import com.grove.Repository.TimeOffRequestRepository;
import com.grove.Repository.UserRepository;

/**
 * Time off requests. An employee asks for dates; anyone whose role has
 * APPROVE_TIME_OFF (set by the owner on Settings) approves it or sends it back
 * with a message. Approved days show as RO on the schedule automatically
 * (see {@link ScheduleService}). Nobody approves their own request.
 */
@Service
public class TimeOffService {

	/** The note on a day that's RO because its time off was approved. */
	public static final String APPROVED_NOTE = "Approved time off";
	private static final int MAX_DAYS = 60;
	private static final int MAX_NOTE = 500;
	private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("MMM d");

	private final TimeOffRequestRepository requests;
	private final BlackoutDateRepository blackouts;
	private final CalendarNoteRepository notes;
	private final UserRepository users;
	private final ShiftRepository shifts;
	private final PermissionService perms;
	private final Mailer mailer;
	private final PushService push;
	private final ZoneId zone;

	public TimeOffService(TimeOffRequestRepository requests, BlackoutDateRepository blackouts, CalendarNoteRepository notes,
			UserRepository users, ShiftRepository shifts,
			PermissionService perms, Mailer mailer, PushService push, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.requests = requests;
		this.blackouts = blackouts;
		this.notes = notes;
		this.users = users;
		this.shifts = shifts;
		this.perms = perms;
		this.mailer = mailer;
		this.push = push;
		this.zone = ZoneId.of(zone);
	}

	/** The viewer's own requests, latest dates first. */
	public List<TimeOffView> mine(User viewer) {
		return requests.findByUserIdOrderByStartDateDesc(viewer.getUserId()).stream().map(this::view).toList();
	}

	/** Anyone who works on the schedule can ask for days off. */
	public TimeOffView request(User employee, TimeOffInput input) {
		if (!employee.getRoles().isScheduled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Time off is for people on the schedule");
		}
		LocalDate start = input == null ? null : input.startDate();
		LocalDate end = input == null ? null : input.endDate();
		if (start == null || end == null) throw bad("Pick the first and last day you want off");
		if (end.isBefore(start)) throw bad("The last day can't be before the first day");
		if (start.isBefore(LocalDate.now(zone))) throw bad("Time off can't start in the past");
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw bad("Ask for " + MAX_DAYS + " days or fewer at a time");
		List<BlackoutDate> blocked = blackouts.findByDateBetweenOrderByDateAsc(start, end);
		if (!blocked.isEmpty()) {
			throw bad((blocked.size() == 1 ? "This day is blacked out, so it can't be requested: " : "These days are blacked out, so they can't be requested: ")
				+ String.join(", ", blocked.stream().map(b -> b.getDate().format(EMAIL_DATE)).toList()));
		}
		boolean overlaps = requests.overlapping(List.of(Status.PENDING, Status.APPROVED), start, end).stream()
			.anyMatch(r -> r.getUserId().equals(employee.getUserId()));
		if (overlaps) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a request that includes some of those days");
		}

		TimeOffRequest r = new TimeOffRequest();
		r.setUserId(employee.getUserId());
		r.setStartDate(start);
		r.setEndDate(end);
		r.setNote(clean(input.note()));
		r.setCreatedAt(LocalDateTime.now(zone));
		TimeOffRequest saved = requests.save(r);

		List<User> approverList = users.findAll().stream()
			.filter(u -> !u.getUserId().equals(employee.getUserId()) && perms.has(u, Permission.APPROVE_TIME_OFF))
			.toList();
		List<String> approvers = approverList.stream().map(User::getEmail).toList();
		push.notify(approverList.stream().map(User::getUserId).toList(), "Time off request",
			name(employee) + ": " + range(saved), "/time-off");
		mailer.send(approvers, "Time off request: " + name(employee), String.join("\n",
			name(employee) + " asked for time off: " + range(saved) + ".",
			saved.getNote() == null ? "" : "\n\"" + saved.getNote() + "\"\n",
			"Approve it or send it back:",
			mailer.link("/time-off")));
		return view(saved);
	}

	/** Blacked-out days from today on, for the request form. Anyone signed in can see these. */
	public List<BlackoutView> upcomingBlackouts() {
		return blackouts.findByDateGreaterThanEqualOrderByDateAsc(LocalDate.now(zone)).stream().map(TimeOffService::view).toList();
	}

	/**
	 * The time off calendar from {@code from} to {@code to}: blackouts plus every waiting or
	 * approved request touching those days. For people who approve time off.
	 */
	public TimeOffCalendar calendar(User viewer, LocalDate from, LocalDate to) {
		requireApprover(viewer);
		if (from == null || to == null || to.isBefore(from)) throw bad("Pick the dates to show");
		if (ChronoUnit.DAYS.between(from, to) > 62) throw bad("Show two months or less at a time");
		return new TimeOffCalendar(from, to,
			blackouts.findByDateBetweenOrderByDateAsc(from, to).stream().map(TimeOffService::view).toList(),
			requests.overlapping(List.of(Status.PENDING, Status.APPROVED), from, to).stream()
				.sorted(java.util.Comparator.comparing(TimeOffRequest::getStartDate))
				.map(this::view).toList(),
			notes.findByDateBetweenOrderByDateAsc(from, to).stream().map(n -> new DayNote(n.getDate(), n.getText())).toList());
	}

	/** Write, change, or (with a blank note) remove the note on a day. */
	public DayNote setNote(User approver, LocalDate date, String text) {
		requireApprover(approver);
		if (date == null) throw bad("Pick a day");
		String t = text == null || text.isBlank() ? null : text.trim();
		if (t == null) {
			notes.findById(date).ifPresent(notes::delete);
			return new DayNote(date, null);
		}
		if (t.length() > 500) t = t.substring(0, 500);
		CalendarNote n = notes.findById(date).orElse(null);
		LocalDateTime now = LocalDateTime.now(zone);
		if (n == null) n = new CalendarNote(date, t, approver.getUserId(), now);
		else {
			n.setText(t);
			n.setUpdatedBy(approver.getUserId());
			n.setUpdatedAt(now);
		}
		CalendarNote saved = notes.save(n);
		return new DayNote(saved.getDate(), saved.getText());
	}

	/** Black out a day (or change its reason). Requests already made for it are left alone. */
	public BlackoutView blackOut(User approver, LocalDate date, String note) {
		requireApprover(approver);
		if (date == null) throw bad("Pick a day");
		String reason = note == null || note.isBlank() ? null : note.trim();
		if (reason != null && reason.length() > 200) reason = reason.substring(0, 200);
		BlackoutDate b = blackouts.findById(date).orElse(null);
		if (b == null) b = new BlackoutDate(date, reason, approver.getUserId(), LocalDateTime.now(zone));
		else b.setNote(reason);
		return view(blackouts.save(b));
	}

	public void clearBlackout(User approver, LocalDate date) {
		requireApprover(approver);
		blackouts.findById(date).ifPresent(blackouts::delete);
	}

	private static BlackoutView view(BlackoutDate b) {
		return new BlackoutView(b.getDate(), b.getNote());
	}

	/**
	 * Take back your own request while it's waiting, or after it's approved up through
	 * its first day. An approved one comes off the schedule and approvers are told, since
	 * they may have planned around it. Once it's under way, an approver can undo it.
	 */
	@Transactional
	public void cancel(User employee, Long id) {
		TimeOffRequest r = find(id);
		if (!r.getUserId().equals(employee.getUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
		if (r.getStatus() == Status.SENT_BACK) throw bad("This request was already sent back");
		boolean wasApproved = r.getStatus() == Status.APPROVED;
		if (wasApproved && r.getStartDate().isBefore(LocalDate.now(zone))) {
			throw bad("This time off has already started. Ask a manager to change it.");
		}
		if (wasApproved) removeApprovedDays(r);
		requests.delete(r);
		if (!wasApproved) return;

		List<User> approverList = users.findAll().stream()
			.filter(u -> !u.getUserId().equals(employee.getUserId()) && perms.has(u, Permission.APPROVE_TIME_OFF))
			.toList();
		push.notify(approverList.stream().map(User::getUserId).toList(), "Approved time off cancelled",
			name(employee) + ": " + range(r), "/schedule");
		mailer.send(approverList.stream().map(User::getEmail).toList(), "Approved time off cancelled: " + name(employee), String.join("\n",
			name(employee) + " cancelled their approved time off: " + range(r) + ".",
			"Those days came off the schedule as RO.",
			"",
			mailer.link("/schedule")));
	}

	/** What an approver sees: everyone else's waiting requests, and approved time off still to come. */
	public TimeOffQueue queue(User approver) {
		requireApprover(approver);
		LocalDate today = LocalDate.now(zone);
		List<TimeOffView> waiting = requests.findByStatusOrderByStartDateAsc(Status.PENDING).stream()
			.filter(r -> !r.getUserId().equals(approver.getUserId()))
			.map(this::view).toList();
		List<TimeOffView> approved = requests.findByStatusOrderByStartDateAsc(Status.APPROVED).stream()
			.filter(r -> !r.getEndDate().isBefore(today) && !r.getUserId().equals(approver.getUserId()))
			.map(this::view).toList();
		return new TimeOffQueue(waiting, approved);
	}

	public TimeOffView approve(User approver, Long id, String message) {
		requireApprover(approver);
		TimeOffRequest r = find(id);
		requireNotOwn(approver, r);
		if (r.getStatus() != Status.PENDING) throw bad("This request has already been answered");
		decide(r, Status.APPROVED, approver, clean(message));
		TimeOffRequest saved = requests.save(r);
		tell(saved, "Time off approved: " + range(saved),
			name(approver) + " approved your time off: " + range(saved) + ".");
		return view(saved);
	}

	/**
	 * Return a request to the employee with a message. Also how an approval is
	 * undone: the RO days it put on the schedule come back off.
	 */
	@Transactional
	public TimeOffView sendBack(User approver, Long id, String message) {
		requireApprover(approver);
		String note = clean(message);
		if (note == null) throw bad("Add a message for them");
		TimeOffRequest r = find(id);
		requireNotOwn(approver, r);
		if (r.getStatus() == Status.SENT_BACK) throw bad("This request was already sent back");
		if (r.getStatus() == Status.APPROVED) removeApprovedDays(r);
		decide(r, Status.SENT_BACK, approver, note);
		TimeOffRequest saved = requests.save(r);
		tell(saved, "Time off sent back: " + range(saved),
			name(approver) + " sent back your time off request for " + range(saved) + ".");
		return view(saved);
	}

	private void removeApprovedDays(TimeOffRequest r) {
		List<Shift> days = shifts.findByUserIdAndDateBetweenOrderByDateAsc(r.getUserId(), r.getStartDate(), r.getEndDate()).stream()
			.filter(s -> s.isRequestedOff() && APPROVED_NOTE.equals(s.getNote()))
			.toList();
		shifts.deleteAll(days);
	}

	private void decide(TimeOffRequest r, Status status, User approver, String message) {
		r.setStatus(status);
		r.setResponseNote(message);
		r.setDecidedBy(approver.getUserId());
		r.setDecidedAt(LocalDateTime.now(zone));
	}

	private void tell(TimeOffRequest r, String subject, String firstLine) {
		push.notify(List.of(r.getUserId()), subject, r.getResponseNote() == null ? firstLine : r.getResponseNote(), "/time-off");
		users.findById(r.getUserId()).ifPresent(u -> mailer.send(List.of(u.getEmail()), subject, String.join("\n",
			firstLine,
			r.getResponseNote() == null ? "" : "\n\"" + r.getResponseNote() + "\"\n",
			mailer.link("/time-off"))));
	}

	private void requireApprover(User user) {
		perms.require(user, Permission.APPROVE_TIME_OFF, "You don't have permission to approve time off");
	}

	private static void requireNotOwn(User approver, TimeOffRequest r) {
		if (r.getUserId().equals(approver.getUserId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Someone else needs to answer your own request");
		}
	}

	private TimeOffRequest find(Long id) {
		return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
	}

	private TimeOffView view(TimeOffRequest r) {
		User u = users.findById(r.getUserId()).orElse(null);
		return new TimeOffView(r.getId(), r.getUserId(), u == null ? "Former employee" : name(u), r.getStartDate(), r.getEndDate(),
			r.getNote(), r.getStatus().name(), r.getResponseNote(),
			r.getDecidedBy() == null ? null : users.findById(r.getDecidedBy()).map(TimeOffService::name).orElse(null),
			r.getDecidedAt(), r.getCreatedAt());
	}

	private static String range(TimeOffRequest r) {
		String first = r.getStartDate().format(EMAIL_DATE);
		return r.getStartDate().equals(r.getEndDate()) ? first : first + " through " + r.getEndDate().format(EMAIL_DATE);
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
