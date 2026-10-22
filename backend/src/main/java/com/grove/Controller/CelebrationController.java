package com.grove.Controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.Celebrations;
import com.grove.Service.CelebrationService;
import com.grove.Service.SessionService;

/** Birthdays and work anniversaries for the Home page. */
@RestController
public class CelebrationController {

	private final CelebrationService celebrations;
	private final SessionService sessions;

	public CelebrationController(CelebrationService celebrations, SessionService sessions) {
		this.celebrations = celebrations;
		this.sessions = sessions;
	}

	@GetMapping("/api/celebrations")
	public Celebrations celebrations(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return celebrations.forViewer(sessions.requireUser(token));
	}
}
