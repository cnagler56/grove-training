package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Service.ClosedDayService;
import com.grove.Service.ClosedDayService.ClosedDayInput;
import com.grove.Service.ClosedDayService.ClosedDayView;
import com.grove.Service.ClosedDayService.HolidayChoice;
import com.grove.Service.ClosedDayService.HolidayInput;
import com.grove.Service.SessionService;

/** Days the salon is closed: marked on the time off calendar, and holidays checked on Settings. */
@RestController
public class ClosedDayController {

	private final ClosedDayService closed;
	private final SessionService sessions;

	public ClosedDayController(ClosedDayService closed, SessionService sessions) {
		this.closed = closed;
		this.sessions = sessions;
	}

	/** Closed days, and holidays opened for one year, between two dates. */
	@GetMapping("/api/closed-days")
	public List<ClosedDayView> between(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		sessions.requireUser(token);
		return closed.between(from, to);
	}

	/** Mark one day closed or open (people who approve time off). */
	@PutMapping("/api/closed-days/{date}")
	public ClosedDayView set(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable LocalDate date, @RequestBody ClosedDayInput body) {
		return closed.set(sessions.requireUser(token), date, body);
	}

	@GetMapping("/api/owner/holidays")
	public List<HolidayChoice> holidays(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return closed.holidayChoices();
	}

	@PutMapping("/api/owner/holidays")
	public List<HolidayChoice> setHoliday(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody HolidayInput body) {
		sessions.requireOwner(token);
		return closed.setHoliday(body);
	}
}
