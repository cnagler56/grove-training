package com.grove.Controller;

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
import org.springframework.web.bind.annotation.RestController;

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
