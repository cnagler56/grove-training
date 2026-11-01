package com.grove.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.PushSubscribeInput;
import com.grove.Domain.Dtos.PushUnsubscribeInput;
import com.grove.Service.PushService;
import com.grove.Service.SessionService;

/** Turning phone notifications on and off for this device. */
@RestController
@RequestMapping("/api/push")
public class PushController {

	private final PushService push;
	private final SessionService sessions;

	public PushController(PushService push, SessionService sessions) {
		this.push = push;
		this.sessions = sessions;
	}

	/** The server's public key. Blank means notifications aren't set up. */
	@GetMapping("/key")
	public Map<String, String> key(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireUser(token);
		return Map.of("publicKey", push.publicKey());
	}

	@PostMapping("/subscribe")
	public ResponseEntity<Void> subscribe(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PushSubscribeInput input) {
		push.subscribe(sessions.requireUser(token), input);
		return ResponseEntity.noContent().build();
	}

	/** Knowing the device's secret address is what proves it's yours. */
	@PostMapping("/unsubscribe")
	public ResponseEntity<Void> unsubscribe(@RequestBody PushUnsubscribeInput input) {
		push.unsubscribe(input == null ? null : input.endpoint());
		return ResponseEntity.noContent().build();
	}
}
