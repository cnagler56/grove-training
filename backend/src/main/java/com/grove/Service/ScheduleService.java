package com.grove.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.PendingWeek;
import com.grove.Domain.Dtos.Person;
import com.grove.Domain.Dtos.RosterEntry;
import com.grove.Domain.Dtos.ShiftInput;
import com.grove.Domain.Dtos.ShiftView;
import com.grove.Domain.Dtos.WeekSchedule;
import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Domain.ScheduleWeek;
import com.grove.Domain.Shift;
import com.grove.Domain.ShiftType;
import com.grove.Domain.User;
import com.grove.Repository.ScheduleWeekRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.UserRepository;

/**
 * The weekly salon schedule. Weeks run Saturday through Friday.
 *
 * Flow: a builder (EDIT_SCHEDULE) fills in a week and sends it to one approver
 * (APPROVE_SCHEDULE), who posts it or sends it back with a note. Approvers can
 * also post directly. Staff only see posted weeks, and once a week is posted
 * only approvers can change it. The owner decides which roles have each
 * permission on the Settings page.
 */
@Service
public class ScheduleService {

	private static final DayOfWeek WEEK_STARTS = DayOfWeek.SATURDAY;
	private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("MMM d");

	private final ShiftRepository shifts;
	private final ScheduleWeekRepository weeks;
	private final UserRepository users;
	private final PermissionService perms;
	private final Mailer mailer;

	public ScheduleService(ShiftRepository shifts, ScheduleWeekRepository weeks, UserRepository users,
			PermissionService perms, Mailer mailer) {
		this.shifts = shifts;
		this.weeks = weeks;
		this.users = users;
		this.perms = perms;
		this.mailer = mailer;
	}

	/** The Saturday on or before {@code day}. */
	public static LocalDate weekStart(LocalDate day) {
		return day.with(TemporalAdjusters.previousOrSame(WEEK_STARTS));
	}

	public WeekSchedule week(User viewer, LocalDate anyDay) {
		LocalDate start = weekStart(anyDay);
		LocalDate end = start.plusDays(6);
		ScheduleWeek w = weeks.findById(start).orElse(null);
		boolean published = w != null && w.isPublished();
		boolean canApprove = perms.has(viewer, Permission.APPROVE_SCHEDULE);
		boolean canBuild = perms.has(viewer, Permission.EDIT_SCHEDULE);
		// Posted weeks are locked to approvers.
		boolean canEdit = canApprove || (canBuild && !published);
		boolean seesDrafts = canApprove || canBuild;
		List<Shift> weekShifts = shifts.findByDateBetweenOrderByDateAscStartAsc(start, end);
		List<ShiftView> list = (published || seesDrafts) ? toViews(weekShifts) : List.of();
		String state = published ? "POSTED" : (w != null && w.isPending()) ? "PENDING" : "DRAFT";
		return new WeekSchedule(start, end, published, w == null ? null : w.getPublishedAt(), state,
			canEdit, canApprove,
			w == null ? null : nameOf(w.getSubmittedBy()),
			w == null ? null : w.getSubmittedTo(),
			w == null ? null : nameOf(w.getSubmittedTo()),
			w == null ? null : w.getSubmittedAt(),
			w == null ? null : w.getReturnNote(),
			w == null ? null : nameOf(w.getReturnedBy()),
			seesDrafts ? approvers(viewer) : List.of(),
			roster(weekShifts), list);
	}

	/**
	 * The Name column: every active stylist, lead, manager, and general manager
	 * (alphabetical), then receptionists. People marked inactive (time off) are
	 * left off. Plus anyone inactive or no longer on staff who still has shifts
	 * that week, so those weeks keep their history.
	 */
	private List<RosterEntry> roster(List<Shift> weekShifts) {
		Set<Long> withShifts = weekShifts.stream().map(Shift::getUserId).collect(Collectors.toSet());
		return users.findAll().stream()
			.filter(u -> (u.isActive() && u.getInactiveSince() == null && u.getRoles().isScheduled())
				|| withShifts.contains(u.getUserId()))
			.sorted(Comparator.comparing((User u) -> u.getRoles() == Role.RECEPTIONIST)
				.thenComparing(u -> name(u).toLowerCase()))
			.map(u -> new RosterEntry(u.getUserId(), name(u), u.getRoles(), u.isActive() && u.getRoles().isScheduled()))
			.toList();
	}

	/** People who can approve, other than {@code viewer}: who a week can be sent to. */
	private List<Person> approvers(User viewer) {
		return users.findAll().stream()
			.filter(u -> !u.getUserId().equals(viewer.getUserId()) && perms.has(u, Permission.APPROVE_SCHEDULE))
			.sorted(Comparator.comparing(u -> name(u).toLowerCase()))
			.map(u -> new Person(u.getUserId(), name(u), u.getRoles()))
			.toList();
	}

	/** The viewer's own shifts from {@code from} through {@code to}, from posted weeks only. */
	public List<ShiftView> mine(User viewer, LocalDate from, LocalDate to) {
		Set<LocalDate> posted = new HashSet<>();
		for (LocalDate s = weekStart(from); !s.isAfter(to); s = s.plusDays(7)) {
			LocalDate ws = s;
			weeks.findById(ws).filter(ScheduleWeek::isPublished).ifPresent(w -> posted.add(ws));
		}
		return toViews(shifts.findByUserIdAndDateBetweenOrderByDateAsc(viewer.getUserId(), from, to).stream()
			.filter(s -> posted.contains(weekStart(s.getDate())))
			.toList());
	}

	/** Weeks sent to {@code approver} that are still waiting (for the Home page). */
	public List<PendingWeek> waitingFor(User approver) {
		if (!perms.has(approver, Permission.APPROVE_SCHEDULE)) return List.of();
		return weeks.findAll().stream()
			.filter(w -> w.isPending() && approver.getUserId().equals(w.getSubmittedTo()))
			.sorted(Comparator.comparing(ScheduleWeek::getWeekStart))
			.map(w -> new PendingWeek(w.getWeekStart(), nameOf(w.getSubmittedBy()), w.getSubmittedAt()))
			.toList();
	}

	/** Replace a whole week with what the editor sent. */
	@Transactional
	public WeekSchedule saveWeek(User editor, LocalDate start, List<ShiftInput> input) {
		requireSaturday(start);
		ScheduleWeek existing = weeks.findById(start).orElse(null);
		boolean canApprove = perms.has(editor, Permission.APPROVE_SCHEDULE);
		if (!canApprove) {
			perms.require(editor, Permission.EDIT_SCHEDULE, "You don't have permission to build the schedule");
			if (existing != null && existing.isPublished()) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"This week is already posted. Only someone who can approve the schedule can change it.");
			}
		}
		LocalDate end = start.plusDays(6);
		List<ShiftInput> list = input == null ? List.of() : input;

		Map<Long, User> staff = users.findAllById(list.stream().map(ShiftInput::userId).filter(id -> id != null).toList())
			.stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
		Set<String> seen = new HashSet<>();
		for (ShiftInput s : list) {
			User u = s.userId() == null ? null : staff.get(s.userId());
			if (u == null || !u.isActive()) throw bad("One of the people on this schedule isn't an active employee");
			if (!u.getRoles().isScheduled()) {
				throw bad(name(u) + " is " + u.getRoles().label().toLowerCase() + ", and that role isn't on the schedule");
			}
			if (s.date() == null || s.date().isBefore(start) || s.date().isAfter(end)) {
				throw bad("Every shift has to fall between " + start + " and " + end);
			}
			if (s.type() != ShiftType.REQUESTED_OFF) {
				if (s.start() == null || s.end() == null) throw bad("Every shift needs a start and end time");
				if (!s.end().isAfter(s.start())) throw bad("A shift for " + name(u) + " ends before it starts");
			}
			if (!seen.add(s.userId() + "@" + s.date())) throw bad(name(u) + " has two shifts on " + s.date());
			if (s.note() != null && s.note().length() > 100) throw bad("Shift notes are limited to 100 characters");
		}

		shifts.deleteByDateBetween(start, end);
		shifts.flush();
		for (ShiftInput s : list) {
			Shift shift = new Shift();
			shift.setUserId(s.userId());
			shift.setDate(s.date());
			boolean off = s.type() == ShiftType.REQUESTED_OFF;
			shift.setType(off ? ShiftType.REQUESTED_OFF : ShiftType.WORK);
			shift.setStart(off ? null : s.start());
			shift.setEnd(off ? null : s.end());
			shift.setNote(s.note() == null || s.note().isBlank() ? null : s.note().trim());
			shifts.save(shift);
		}
		ScheduleWeek w = existing != null ? existing : newWeek(start);
		w.setUpdatedAt(LocalDateTime.now());
		weeks.save(w);
		return week(editor, start);
	}

	/** A builder sends the (saved) week to one approver, who's emailed about it. */
	public WeekSchedule submit(User builder, LocalDate start, Long approverId) {
		requireSaturday(start);
		perms.require(builder, Permission.EDIT_SCHEDULE, "You don't have permission to build the schedule");
		ScheduleWeek w = weeks.findById(start).orElseGet(() -> newWeek(start));
		if (w.isPublished()) throw bad("This week is already posted");
		User approver = approverId == null ? null : users.findById(approverId).orElse(null);
		if (approver == null || !perms.has(approver, Permission.APPROVE_SCHEDULE)) {
			throw bad("Pick someone who can approve the schedule");
		}
		if (approver.getUserId().equals(builder.getUserId())) {
			throw bad("Send it to someone else, or post it yourself");
		}
		w.setSubmittedBy(builder.getUserId());
		w.setSubmittedTo(approver.getUserId());
		w.setSubmittedAt(LocalDateTime.now());
		w.setReturnNote(null);
		w.setReturnedBy(null);
		weeks.save(w);

		String range = range(start);
		mailer.send(List.of(approver.getEmail()), "Schedule to approve: week of " + range, String.join("\n",
			name(builder) + " sent you the schedule for the week of " + range + " to approve.",
			"",
			"Review it, then approve and post it to staff or send it back:",
			mailer.link("/schedule/edit?week=" + start)));
		return week(builder, start);
	}

	/** Approver posts a week for staff (clearing any pending approval), or takes it down. */
	public WeekSchedule setPublished(User approver, LocalDate start, boolean published) {
		requireSaturday(start);
		perms.require(approver, Permission.APPROVE_SCHEDULE, "Only people who can approve the schedule can post it");
		ScheduleWeek w = weeks.findById(start).orElseGet(() -> newWeek(start));
		Long builderId = w.getSubmittedBy();
		w.setPublished(published);
		w.setPublishedAt(published ? LocalDateTime.now() : null);
		if (published) {
			w.clearSubmission();
			w.setReturnNote(null);
			w.setReturnedBy(null);
		}
		weeks.save(w);

		// Let the builder know their week went up.
		if (published && builderId != null && !builderId.equals(approver.getUserId())) {
			users.findById(builderId).ifPresent(b -> mailer.send(List.of(b.getEmail()),
				"Schedule posted: week of " + range(start),
				name(approver) + " approved and posted the schedule for the week of " + range(start) + "."));
		}
		return week(approver, start);
	}

	/** Approver returns a pending week to its builder with a note. */
	public WeekSchedule sendBack(User approver, LocalDate start, String note) {
		requireSaturday(start);
		perms.require(approver, Permission.APPROVE_SCHEDULE, "Only people who can approve the schedule can send it back");
		ScheduleWeek w = weeks.findById(start).orElseThrow(() -> bad("This week hasn't been sent for approval"));
		if (!w.isPending()) throw bad("This week isn't waiting for approval");
		String trimmed = note == null ? "" : note.trim();
		if (trimmed.isEmpty()) throw bad("Add a note so they know what to change");
		if (trimmed.length() > 500) throw bad("Keep the note under 500 characters");
		Long builderId = w.getSubmittedBy();
		w.clearSubmission();
		w.setReturnNote(trimmed);
		w.setReturnedBy(approver.getUserId());
		weeks.save(w);

		if (builderId != null) {
			users.findById(builderId).ifPresent(b -> mailer.send(List.of(b.getEmail()),
				"Schedule sent back: week of " + range(start), String.join("\n",
					name(approver) + " sent back the schedule for the week of " + range(start) + ":",
					"",
					trimmed,
					"",
					mailer.link("/schedule/edit?week=" + start))));
		}
		return week(approver, start);
	}

	// ---- helpers -------------------------------------------------------------

	private static ScheduleWeek newWeek(LocalDate start) {
		ScheduleWeek w = new ScheduleWeek();
		w.setWeekStart(start);
		return w;
	}

	private static String range(LocalDate start) {
		return EMAIL_DATE.format(start) + " – " + EMAIL_DATE.format(start.plusDays(6));
	}

	private String nameOf(Long userId) {
		return userId == null ? null : users.findById(userId).map(ScheduleService::name).orElse("Former employee");
	}

	private List<ShiftView> toViews(List<Shift> list) {
		Map<Long, User> people = users.findAllById(list.stream().map(Shift::getUserId).distinct().toList())
			.stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
		return list.stream().map(s -> {
			User u = people.get(s.getUserId());
			return new ShiftView(s.getId(), s.getUserId(), u == null ? "Former employee" : name(u),
				u == null ? null : u.getRoles(), s.getDate(), s.getType(), s.getStart(), s.getEnd(), s.getNote());
		}).toList();
	}

	private static void requireSaturday(LocalDate start) {
		if (start.getDayOfWeek() != WEEK_STARTS) throw bad("Schedule weeks start on a Saturday");
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
			+ (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static ResponseStatusException bad(String msg) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}
}
