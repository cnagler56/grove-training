package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.ShiftView;
import com.grove.Domain.Dtos.WeekPublish;
import com.grove.Domain.Dtos.WeekSave;
import com.grove.Domain.Dtos.WeekSchedule;
import com.grove.Domain.User;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;

/** Weekly schedule: staff read posted weeks; owners and general managers edit and post. */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

	private final ScheduleService schedule;
	private final SessionService sessions;

	public ScheduleController(ScheduleService schedule, SessionService sessions) {
		this.schedule = schedule;
		this.sessions = sessions;
	}

	/** The week containing {@code date} (default: today). */
	@GetMapping
	public WeekSchedule week(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.week(requireStaff(token), date == null ? LocalDate.now() : date);
	}

	/** My shifts for this week and next (posted weeks only). */
	@GetMapping("/mine")
	public List<ShiftView> mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		LocalDate today = LocalDate.now();
		return schedule.mine(requireStaff(token), today, ScheduleService.weekStart(today).plusDays(13));
	}

	@PutMapping("/{weekStart}")
	public WeekSchedule save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekSave body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.saveWeek(requireEditor(token), weekStart, body.shifts());
	}

	@PutMapping("/{weekStart}/published")
	public WeekSchedule publish(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestBody WeekPublish body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return schedule.setPublished(requireEditor(token), weekStart, body.published());
	}

	private User requireStaff(String token) {
		User user = sessions.requireUser(token);
		if (!user.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The schedule is for staff");
		return user;
	}

	private User requireEditor(String token) {
		User user = sessions.requireUser(token);
		if (!user.getRoles().canEditSchedule()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners and general managers can edit the schedule");
		}
		return user;
	}
}
