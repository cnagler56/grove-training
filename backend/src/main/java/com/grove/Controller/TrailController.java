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

import com.grove.Domain.TrailParty.Pace;
import com.grove.Service.SessionService;
import com.grove.Service.TrailService;
import com.grove.Service.TrailService.BuyInput;
import com.grove.Service.TrailService.Eligible;
import com.grove.Service.TrailService.GameInput;
import com.grove.Service.TrailService.GameView;

/** The Trail tab: everyone on staff sees the games; players play; managers and up start them. */
@RestController
@RequestMapping("/api/trail")
public class TrailController {

	public record ChooseBody(Integer option) {}
	public record PaceBody(Pace pace) {}
	public record InviteBody(Long userId) {}
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

	/** Managers: play the next turn now. */
	@PostMapping("/{id}/turn")
	public GameView turn(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable Long id) {
		return trail.runTurnNow(sessions.requireUser(token), id);
	}

	@PostMapping("/{id}/choose")
	public GameView choose(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody ChooseBody body) {
		return trail.choose(sessions.requireUser(token), id, body == null || body.option() == null ? -1 : body.option());
	}

	@PostMapping("/{id}/buy")
	public GameView buy(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody BuyInput body) {
		return trail.buy(sessions.requireUser(token), id, body);
	}

	@PostMapping("/{id}/pace")
	public GameView pace(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody PaceBody body) {
		return trail.setPace(sessions.requireUser(token), id, body == null ? null : body.pace());
	}

	@PostMapping("/{id}/invite")
	public GameView invite(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody InviteBody body) {
		return trail.invite(sessions.requireUser(token), id, body == null ? null : body.userId());
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
