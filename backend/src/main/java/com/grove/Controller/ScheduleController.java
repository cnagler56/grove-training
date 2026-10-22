package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.PendingWeek;
import com.grove.Domain.Dtos.ShiftView;
import com.grove.Domain.Dtos.WeekPublish;
import com.grove.Domain.Dtos.WeekSave;
import com.grove.Domain.Dtos.WeekSchedule;
import com.grove.Domain.Dtos.WeekSendBack;
import com.grove.Domain.Dtos.WeekSubmit;
import com.grove.Domain.Permission;
import com.grove.Domain.User;
import com.grove.Service.PermissionService;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;

/** Weekly schedule: view, build, send for approval, approve/post. Permissions are set by the owner. */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

	private final ScheduleService schedule;
	private final SessionService sessions;
	private final PermissionService perms;

	public ScheduleController(ScheduleService schedule, SessionService sessions, PermissionService perms) {
		this.schedule = schedule;
		this.sessions = sessions;
		this.perms = perms;
	}

	/** The week containing {@code date} (default: today). */
	@GetMapping
	public WeekSchedule week(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.week(requireViewer(token), date == null ? LocalDate.now() : date);
	}

	/** My shifts for this week and next (posted weeks only). Anyone on staff; shown on Home. */
	@GetMapping("/mine")
	public List<ShiftView> mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User user = sessions.requireUser(token);
		if (!user.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The schedule is for staff");
		LocalDate today = LocalDate.now();
		return schedule.mine(user, today, ScheduleService.weekStart(today).plusDays(13));
	}

	/** Weeks waiting for my approval. */
	@GetMapping("/waiting")
	public List<PendingWeek> waiting(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.waitingFor(sessions.requireUser(token));
	}

	@PutMapping("/{weekStart}")
	public WeekSchedule save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekSave body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.saveWeek(sessions.requireUser(token), weekStart, body.shifts());
	}

	@PostMapping("/{weekStart}/submit")
	public WeekSchedule submit(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekSubmit body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.submit(sessions.requireUser(token), weekStart, body.approverId());
	}

	@PostMapping("/{weekStart}/send-back")
	public WeekSchedule sendBack(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekSendBack body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.sendBack(sessions.requireUser(token), weekStart, body.note());
	}

	@PutMapping("/{weekStart}/published")
	public WeekSchedule publish(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekPublish body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.setPublished(sessions.requireUser(token), weekStart, body.published());
	}

	/** Viewing a week: the Schedule tab permission, or anyone who builds or approves it. */
	private User requireViewer(String token) {
		User user = sessions.requireUser(token);
		if (!perms.has(user, Permission.VIEW_SCHEDULE) && !perms.has(user, Permission.EDIT_SCHEDULE)
				&& !perms.has(user, Permission.APPROVE_SCHEDULE)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role doesn't have access to the schedule");
		}
		return user;
	}
}
