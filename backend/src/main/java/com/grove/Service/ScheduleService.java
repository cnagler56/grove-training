package com.grove.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.grove.Domain.Dtos.RosterEntry;
import com.grove.Domain.Dtos.ShiftInput;
import com.grove.Domain.Dtos.ShiftView;
import com.grove.Domain.Dtos.WeekSchedule;
import com.grove.Domain.Role;
import com.grove.Domain.ScheduleWeek;
import com.grove.Domain.Shift;
import com.grove.Domain.User;
import com.grove.Repository.ScheduleWeekRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.UserRepository;

/**
 * The weekly salon schedule. Weeks run Saturday through Friday. Owners and
 * general managers fill a week in and post it; until then, staff see nothing
 * for that week so they never read a half-finished schedule.
 */
@Service
public class ScheduleService {

	private static final DayOfWeek WEEK_STARTS = DayOfWeek.SATURDAY;

	private final ShiftRepository shifts;
	private final ScheduleWeekRepository weeks;
	private final UserRepository users;

	public ScheduleService(ShiftRepository shifts, ScheduleWeekRepository weeks, UserRepository users) {
		this.shifts = shifts;
		this.weeks = weeks;
		this.users = users;
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
		boolean canEdit = viewer.getRoles().canEditSchedule();
		List<Shift> weekShifts = shifts.findByDateBetweenOrderByDateAscStartAsc(start, end);
		List<ShiftView> list = (published || canEdit) ? toViews(weekShifts) : List.of();
		return new WeekSchedule(start, end, published, w == null ? null : w.getPublishedAt(), canEdit,
			roster(weekShifts), list);
	}

	/**
	 * The Name column: every active stylist, lead, manager, and general manager
	 * (alphabetical), then receptionists. Plus anyone no longer on staff who
	 * still has shifts that week, so old weeks keep their history.
	 */
	private List<RosterEntry> roster(List<Shift> weekShifts) {
		Set<Long> withShifts = weekShifts.stream().map(Shift::getUserId).collect(Collectors.toSet());
		return users.findAll().stream()
			.filter(u -> (u.isActive() && u.getRoles().isScheduled()) || withShifts.contains(u.getUserId()))
			.sorted(Comparator.comparing((User u) -> u.getRoles() == Role.RECEPTIONIST)
				.thenComparing(u -> name(u).toLowerCase()))
			.map(u -> new RosterEntry(u.getUserId(), name(u), u.getRoles(), u.isActive() && u.getRoles().isScheduled()))
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

	/** Replace a whole week with what the editor sent. */
	@Transactional
	public WeekSchedule saveWeek(User editor, LocalDate start, List<ShiftInput> input) {
		requireSaturday(start);
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
			if (s.start() == null || s.end() == null) throw bad("Every shift needs a start and end time");
			if (!s.end().isAfter(s.start())) throw bad("A shift for " + name(u) + " ends before it starts");
			if (!seen.add(s.userId() + "@" + s.date())) throw bad(name(u) + " has two shifts on " + s.date());
			if (s.note() != null && s.note().length() > 100) throw bad("Shift notes are limited to 100 characters");
		}

		shifts.deleteByDateBetween(start, end);
		shifts.flush();
		for (ShiftInput s : list) {
			Shift shift = new Shift();
			shift.setUserId(s.userId());
			shift.setDate(s.date());
			shift.setStart(s.start());
			shift.setEnd(s.end());
			shift.setNote(s.note() == null || s.note().isBlank() ? null : s.note().trim());
			shifts.save(shift);
		}
		ScheduleWeek w = weeks.findById(start).orElseGet(() -> {
			ScheduleWeek nw = new ScheduleWeek();
			nw.setWeekStart(start);
			return nw;
		});
		w.setUpdatedAt(LocalDateTime.now());
		weeks.save(w);
		return week(editor, start);
	}

	/** Post a week for staff to see, or take it back down. */
	public WeekSchedule setPublished(User editor, LocalDate start, boolean published) {
		requireSaturday(start);
		ScheduleWeek w = weeks.findById(start).orElseGet(() -> {
			ScheduleWeek nw = new ScheduleWeek();
			nw.setWeekStart(start);
			return nw;
		});
		w.setPublished(published);
		w.setPublishedAt(published ? LocalDateTime.now() : null);
		weeks.save(w);
		return week(editor, start);
	}

	// ---- helpers -------------------------------------------------------------

	private List<ShiftView> toViews(List<Shift> list) {
		Map<Long, User> people = users.findAllById(list.stream().map(Shift::getUserId).distinct().toList())
			.stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
		return list.stream().map(s -> {
			User u = people.get(s.getUserId());
			return new ShiftView(s.getId(), s.getUserId(), u == null ? "Former employee" : name(u),
				u == null ? null : u.getRoles(), s.getDate(), s.getStart(), s.getEnd(), s.getNote());
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
