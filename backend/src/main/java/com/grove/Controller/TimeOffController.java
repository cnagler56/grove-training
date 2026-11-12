package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.BlackoutInput;
import com.grove.Domain.Dtos.BlackoutView;
import com.grove.Domain.Dtos.DayNote;
import com.grove.Domain.Dtos.TimeOffCalendar;
import com.grove.Domain.Dtos.TimeOffDecision;
import com.grove.Domain.Dtos.TimeOffInput;
import com.grove.Domain.Dtos.TimeOffQueue;
import com.grove.Domain.Dtos.TimeOffView;
import com.grove.Service.SessionService;
import com.grove.Service.TimeOffService;

/** The Time off tab: employees ask for days off; approvers answer. */
@RestController
@RequestMapping("/api/time-off")
public class TimeOffController {

	private final TimeOffService timeOff;
	private final SessionService sessions;

	public TimeOffController(TimeOffService timeOff, SessionService sessions) {
		this.timeOff = timeOff;
		this.sessions = sessions;
	}

	@GetMapping("/mine")
	public List<TimeOffView> mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return timeOff.mine(sessions.requireUser(token));
	}

	@PostMapping
	public ResponseEntity<TimeOffView> request(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody TimeOffInput input) {
		return ResponseEntity.status(HttpStatus.CREATED).body(timeOff.request(sessions.requireUser(token), input));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> cancel(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		timeOff.cancel(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	/** Blacked-out days from today on, for anyone asking for time off. */
	@GetMapping("/blackouts")
	public List<BlackoutView> blackouts(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireUser(token);
		return timeOff.upcomingBlackouts();
	}

	/** The time off calendar: blackouts and time off between two dates. */
	@GetMapping("/calendar")
	public TimeOffCalendar calendar(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return timeOff.calendar(sessions.requireUser(token), from, to);
	}

	@PutMapping("/blackouts/{date}")
	public BlackoutView blackOut(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable LocalDate date, @RequestBody(required = false) BlackoutInput body) {
		return timeOff.blackOut(sessions.requireUser(token), date, body == null ? null : body.note());
	}

	@DeleteMapping("/blackouts/{date}")
	public ResponseEntity<Void> clearBlackout(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable LocalDate date) {
		timeOff.clearBlackout(sessions.requireUser(token), date);
		return ResponseEntity.noContent().build();
	}

	/** The note on a day of the calendar; a blank note removes it. */
	@PutMapping("/notes/{date}")
	public DayNote note(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable LocalDate date, @RequestBody(required = false) BlackoutInput body) {
		return timeOff.setNote(sessions.requireUser(token), date, body == null ? null : body.note());
	}

	@GetMapping("/queue")
	public TimeOffQueue queue(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return timeOff.queue(sessions.requireUser(token));
	}

	@PutMapping("/{id}/approve")
	public TimeOffView approve(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody(required = false) TimeOffDecision body) {
		return timeOff.approve(sessions.requireUser(token), id, body == null ? null : body.message());
	}

	@PutMapping("/{id}/send-back")
	public TimeOffView sendBack(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody TimeOffDecision body) {
		return timeOff.sendBack(sessions.requireUser(token), id, body == null ? null : body.message());
	}
}
