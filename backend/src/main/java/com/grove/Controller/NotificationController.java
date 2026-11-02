package com.grove.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Service.NotificationService;
import com.grove.Service.NotificationService.Inbox;
import com.grove.Service.SessionService;

/** The Notifications list: what the app has told you, and how many you haven't seen. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	/** The page you just opened. */
	public record SeenInput(String path) {}

	public record Unread(long unread) {}

	private final NotificationService notifications;
	private final SessionService sessions;

	public NotificationController(NotificationService notifications, SessionService sessions) {
		this.notifications = notifications;
		this.sessions = sessions;
	}

	@GetMapping
	public Inbox inbox(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return notifications.inbox(sessions.requireUser(token));
	}

	/** Marks anything pointing to this page read, and returns the unread count. */
	@PostMapping("/seen")
	public Unread seen(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody SeenInput body) {
		return new Unread(notifications.seen(sessions.requireUser(token), body == null ? null : body.path()));
	}

	@PutMapping("/{id}/read")
	public ResponseEntity<Void> read(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		notifications.read(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/read-all")
	public ResponseEntity<Void> readAll(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		notifications.readAll(sessions.requireUser(token));
		return ResponseEntity.noContent().build();
	}
}
