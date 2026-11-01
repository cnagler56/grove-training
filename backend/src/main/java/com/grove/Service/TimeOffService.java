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
import com.grove.Domain.TimeOffRequest;
import com.grove.Domain.TimeOffRequest.Status;
import com.grove.Domain.User;
import com.grove.Repository.ShiftRepository;
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
	private final UserRepository users;
	private final ShiftRepository shifts;
	private final PermissionService perms;
	private final Mailer mailer;
	private final ZoneId zone;

	public TimeOffService(TimeOffRequestRepository requests, UserRepository users, ShiftRepository shifts,
			PermissionService perms, Mailer mailer, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.requests = requests;
		this.users = users;
		this.shifts = shifts;
		this.perms = perms;
		this.mailer = mailer;
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

		List<String> approvers = users.findAll().stream()
			.filter(u -> !u.getUserId().equals(employee.getUserId()) && perms.has(u, Permission.APPROVE_TIME_OFF))
			.map(User::getEmail).toList();
		mailer.send(approvers, "Time off request: " + name(employee), String.join("\n",
			name(employee) + " asked for time off: " + range(saved) + ".",
			saved.getNote() == null ? "" : "\n\"" + saved.getNote() + "\"\n",
			"Approve it or send it back:",
			mailer.link("/time-off")));
		return view(saved);
	}

	/** Take back your own request while it's still waiting. */
	public void cancel(User employee, Long id) {
		TimeOffRequest r = find(id);
		if (!r.getUserId().equals(employee.getUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
		if (r.getStatus() != Status.PENDING) throw bad("Only a request that's still waiting can be cancelled");
		requests.delete(r);
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
