package com.grove.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.VacationPayInput;
import com.grove.Domain.Dtos.VacationPayQueue;
import com.grove.Domain.Dtos.VacationPayView;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Domain.VacationPayRequest;
import com.grove.Domain.VacationPayRequest.Status;
import com.grove.Repository.UserRepository;
import com.grove.Repository.VacationPayRequestRepository;

/**
 * Vacation pay requests. An employee asks for a number of hours on an upcoming
 * check date; owners approve it or send it back with a message. Hours are pay,
 * so only owners see other people's requests (the controller enforces that).
 */
@Service
public class VacationPayService {

	/** The first check date. They repeat every {@link #DAYS_BETWEEN_CHECKS} days from here. */
	static final LocalDate FIRST_CHECK_DATE = LocalDate.of(2026, 11, 13);
	static final int DAYS_BETWEEN_CHECKS = 14;
	/** How many upcoming check dates can be picked. */
	static final int CHECK_DATES_OFFERED = 8;
	private static final BigDecimal MAX_HOURS = new BigDecimal("400");
	private static final int MAX_NOTE = 500;
	private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("MMM d");

	private final VacationPayRequestRepository requests;
	private final UserRepository users;
	private final Mailer mailer;
	private final PushService push;
	private final ZoneId zone;

	public VacationPayService(VacationPayRequestRepository requests, UserRepository users, Mailer mailer, PushService push,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.requests = requests;
		this.users = users;
		this.mailer = mailer;
		this.push = push;
		this.zone = ZoneId.of(zone);
	}

	/** The next check dates after {@code today}. A check going out today is too late to ask for. */
	public static List<LocalDate> checkDatesAfter(LocalDate today) {
		long checksPassed = today.isBefore(FIRST_CHECK_DATE)
			? 0 : ChronoUnit.DAYS.between(FIRST_CHECK_DATE, today) / DAYS_BETWEEN_CHECKS + 1;
		List<LocalDate> dates = new ArrayList<>();
		for (long i = 0; i < CHECK_DATES_OFFERED; i++) {
			dates.add(FIRST_CHECK_DATE.plusDays((checksPassed + i) * DAYS_BETWEEN_CHECKS));
		}
		return dates;
	}

	public List<LocalDate> checkDates() {
		return checkDatesAfter(LocalDate.now(zone));
	}

	/** The viewer's own requests, latest check date first. */
	public List<VacationPayView> mine(User viewer) {
		return requests.findByUserIdOrderByCheckDateDesc(viewer.getUserId()).stream().map(this::view).toList();
	}

	/** Anyone who works on the schedule can ask, the same people as time off. */
	public VacationPayView request(User employee, VacationPayInput input) {
		if (!employee.getRoles().isScheduled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vacation pay is for people on the schedule");
		}
		BigDecimal hours = input == null ? null : input.hours();
		LocalDate checkDate = input == null ? null : input.checkDate();
		if (hours == null || hours.signum() <= 0) throw bad("Enter how many hours, more than 0");
		if (hours.compareTo(MAX_HOURS) > 0) throw bad("Ask for " + MAX_HOURS + " hours or fewer at a time");
		if (hours.stripTrailingZeros().scale() > 2) throw bad("Hours can have at most two decimal places");
		if (checkDate == null) throw bad("Pick a check date");
		if (!checkDates().contains(checkDate)) throw bad("Pick one of the upcoming check dates");
		if (requests.existsByUserIdAndCheckDateAndStatusIn(employee.getUserId(), checkDate, List.of(Status.PENDING, Status.APPROVED))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a request for that check date");
		}

		VacationPayRequest r = new VacationPayRequest();
		r.setUserId(employee.getUserId());
		r.setHours(hours);
		r.setCheckDate(checkDate);
		r.setCreatedAt(LocalDateTime.now(zone));
		VacationPayRequest saved = requests.save(r);

		List<User> owners = users.findAll().stream().filter(u -> u.isActive() && u.getRoles() == Role.OWNER).toList();
		push.notify(owners.stream().map(User::getUserId).toList(), "Vacation pay request",
			name(employee) + ": " + summary(saved), "/vacation-pay");
		mailer.send(owners.stream().map(User::getEmail).toList(), "Vacation pay request: " + name(employee), String.join("\n",
			name(employee) + " asked for vacation pay: " + summary(saved) + ".",
			"",
			"Approve it or send it back:",
			mailer.link("/vacation-pay")));
		return view(saved);
	}

	/** Take back your own request while it's still waiting. */
	public void cancel(User employee, Long id) {
		VacationPayRequest r = find(id);
		if (!r.getUserId().equals(employee.getUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
		if (r.getStatus() != Status.PENDING) throw bad("Only a request that's still waiting can be cancelled");
		requests.delete(r);
	}

	/** What an owner sees: waiting requests, and approved ones whose check hasn't gone out yet. */
	public VacationPayQueue queue() {
		LocalDate today = LocalDate.now(zone);
		List<VacationPayView> waiting = requests.findByStatusOrderByCheckDateAsc(Status.PENDING).stream().map(this::view).toList();
		List<VacationPayView> approved = requests.findByStatusOrderByCheckDateAsc(Status.APPROVED).stream()
			.filter(r -> !r.getCheckDate().isBefore(today))
			.map(this::view).toList();
		return new VacationPayQueue(waiting, approved);
	}

	public VacationPayView approve(User owner, Long id) {
		VacationPayRequest r = find(id);
		if (r.getStatus() != Status.PENDING) throw bad("This request has already been answered");
		decide(r, Status.APPROVED, owner, null);
		VacationPayRequest saved = requests.save(r);
		tell(saved, "Vacation pay approved: " + summary(saved),
			name(owner) + " approved your vacation pay: " + summary(saved) + ".");
		return view(saved);
	}

	/** Return a request to the employee with a message. Also how an approval is undone. */
	public VacationPayView sendBack(User owner, Long id, String message) {
		String note = clean(message);
		if (note == null) throw bad("Add a message for them");
		VacationPayRequest r = find(id);
		if (r.getStatus() == Status.SENT_BACK) throw bad("This request was already sent back");
		decide(r, Status.SENT_BACK, owner, note);
		VacationPayRequest saved = requests.save(r);
		tell(saved, "Vacation pay sent back: " + summary(saved),
			name(owner) + " sent back your vacation pay request: " + summary(saved) + ".");
		return view(saved);
	}

	private void decide(VacationPayRequest r, Status status, User owner, String message) {
		r.setStatus(status);
		r.setResponseNote(message);
		r.setDecidedBy(owner.getUserId());
		r.setDecidedAt(LocalDateTime.now(zone));
	}

	private void tell(VacationPayRequest r, String subject, String firstLine) {
		push.notify(List.of(r.getUserId()), subject, r.getResponseNote() == null ? firstLine : r.getResponseNote(), "/vacation-pay");
		users.findById(r.getUserId()).ifPresent(u -> mailer.send(List.of(u.getEmail()), subject, String.join("\n",
			firstLine,
			r.getResponseNote() == null ? "" : "\n\"" + r.getResponseNote() + "\"\n",
			mailer.link("/vacation-pay"))));
	}

	private VacationPayRequest find(Long id) {
		return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
	}

	private VacationPayView view(VacationPayRequest r) {
		User u = users.findById(r.getUserId()).orElse(null);
		return new VacationPayView(r.getId(), r.getUserId(), u == null ? "Former employee" : name(u), r.getHours(), r.getCheckDate(),
			r.getStatus().name(), r.getResponseNote(),
			r.getDecidedBy() == null ? null : users.findById(r.getDecidedBy()).map(VacationPayService::name).orElse(null),
			r.getDecidedAt(), r.getCreatedAt());
	}

	private static String summary(VacationPayRequest r) {
		return r.getHours().stripTrailingZeros().toPlainString() + " hours on the " + r.getCheckDate().format(EMAIL_DATE) + " check";
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
