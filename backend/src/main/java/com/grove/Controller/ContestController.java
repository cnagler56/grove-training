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

import com.grove.Service.ContestService;
import com.grove.Service.ContestService.ContestInput;
import com.grove.Service.ContestService.ContestView;
import com.grove.Service.ContestService.Eligible;
import com.grove.Service.ContestService.WorthPreviewInput;
import com.grove.Service.ContestService.WorthView;
import com.grove.Service.SessionService;

/** The Contests tab: everyone on staff sees the standings; managers and up run contests. */
@RestController
@RequestMapping("/api/contests")
public class ContestController {

	private final ContestService contests;
	private final SessionService sessions;

	public ContestController(ContestService contests, SessionService sessions) {
		this.contests = contests;
		this.sessions = sessions;
	}

	@GetMapping
	public List<ContestView> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return contests.list(sessions.requireUser(token));
	}

	@GetMapping("/people")
	public List<Eligible> people(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return contests.eligible(sessions.requireUser(token));
	}

	/** What each person's service would be worth with a handicap, while setting a contest up. */
	@PostMapping("/worth-preview")
	public List<WorthView> previewWorth(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody WorthPreviewInput body) {
		return contests.previewWorth(sessions.requireUser(token), body);
	}

	@PostMapping
	public ResponseEntity<ContestView> create(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ContestInput body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(contests.create(sessions.requireUser(token), body));
	}

	@PutMapping("/{id}")
	public ContestView update(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody ContestInput body) {
		return contests.update(sessions.requireUser(token), id, body);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		contests.delete(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/check")
	public ContestView checkNow(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return contests.checkNow(sessions.requireUser(token), id);
	}
}
