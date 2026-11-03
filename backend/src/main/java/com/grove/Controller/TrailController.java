package com.grove.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Service.SessionService;
import com.grove.Service.TrailService;
import com.grove.Service.TrailService.BuyInput;
import com.grove.Service.TrailService.Eligible;
import com.grove.Service.TrailService.GameInput;
import com.grove.Service.TrailService.GameView;
import com.grove.Service.TrailService.PlayInput;

/** The Trail tab: everyone on staff sees the games; players play; managers and up start them. */
@RestController
@RequestMapping("/api/trail")
public class TrailController {

	public record AnswerBody(String answer) {}

	private final TrailService trail;
	private final SessionService sessions;

	public TrailController(TrailService trail, SessionService sessions) {
		this.trail = trail;
		this.sessions = sessions;
	}

	@GetMapping
	public List<GameView> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return trail.list(sessions.requireUser(token));
	}

	@GetMapping("/people")
	public List<Eligible> people(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return trail.eligible(sessions.requireUser(token));
	}

	@GetMapping("/{id}")
	public GameView get(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable Long id) {
		return trail.get(sessions.requireUser(token), id);
	}

	@PostMapping
	public ResponseEntity<GameView> create(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody GameInput body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(trail.create(sessions.requireUser(token), body));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		trail.delete(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	/** Managers: unlock the next turn for everyone now instead of waiting for the next check. */
	@PostMapping("/{id}/unlock")
	public GameView unlock(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable Long id) {
		return trail.unlockNow(sessions.requireUser(token), id);
	}

	/** Managers: end the game now and send out the standings. */
	@PostMapping("/{id}/end")
	public GameView end(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable Long id) {
		return trail.endNow(sessions.requireUser(token), id);
	}

	/** Go: play your party's next turn with the option you picked. */
	@PostMapping("/{id}/play")
	public GameView play(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody(required = false) PlayInput body) {
		return trail.play(sessions.requireUser(token), id, body);
	}

	@PostMapping("/{id}/buy")
	public GameView buy(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody BuyInput body) {
		return trail.buy(sessions.requireUser(token), id, body);
	}

	@PostMapping("/{id}/choices/{choiceId}")
	public GameView answer(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @PathVariable Long choiceId, @RequestBody AnswerBody body) {
		return trail.answer(sessions.requireUser(token), id, choiceId, body == null ? null : body.answer());
	}

	@PostMapping("/{id}/leave")
	public GameView leave(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable Long id) {
		return trail.leave(sessions.requireUser(token), id);
	}

	@PostMapping("/{id}/abandon-wagon")
	public GameView abandonWagon(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return trail.abandonWagon(sessions.requireUser(token), id);
	}
}
