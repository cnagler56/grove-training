package com.grove.Controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Permission;
import com.grove.Service.AppointmentBookService;
import com.grove.Service.GoalsService;
import com.grove.Service.PermissionService;
import com.grove.Service.SessionService;

/** Monthly goals: the owner sets them in Settings; whoever Settings allows sees the Home card. */
@RestController
public class GoalsController {

	private final GoalsService goals;
	private final SessionService sessions;
	private final PermissionService perms;
	private final AppointmentBookService book;

	public GoalsController(GoalsService goals, SessionService sessions, PermissionService perms, AppointmentBookService book) {
		this.goals = goals;
		this.sessions = sessions;
		this.perms = perms;
		this.book = book;
	}

	@GetMapping("/api/goals")
	public GoalsService.GoalsView current(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		perms.require(sessions.requireUser(token), Permission.VIEW_GOALS, "You can't see the monthly goals");
		return goals.view();
	}

	/** What's booked in the appointment book for a day (today up to two weeks ahead). */
	@GetMapping("/api/goals/booked")
	public AppointmentBookService.Booked booked(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam String date) {
		perms.require(sessions.requireUser(token), Permission.VIEW_GOALS, "You can't see the monthly goals");
		try {
			return book.booked(LocalDate.parse(date));
		} catch (DateTimeParseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date should look like 2026-11-23");
		}
	}

	@GetMapping("/api/owner/goals/{month}")
	public GoalsService.Goals get(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable String month) {
		sessions.requireOwner(token);
		return goals.get(month(month));
	}

	@PutMapping("/api/owner/goals/{month}")
	public GoalsService.Goals set(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable String month, @RequestBody GoalsService.Goals body) {
		sessions.requireOwner(token);
		return goals.set(month(month), body);
	}

	private static YearMonth month(String s) {
		try {
			return YearMonth.parse(s);
		} catch (DateTimeParseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month should look like 2026-11");
		}
	}
}
