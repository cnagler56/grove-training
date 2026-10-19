package com.grove.Controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.LoginDTO;
import com.grove.Domain.User;
import com.grove.Service.SessionService;
import com.grove.Service.UserService;

/**
 * Cookie-session auth. Register / login mint a server-side session and set an
 * HttpOnly cookie; /me resolves the current user from it; /logout clears it.
 *
 * Locally the cookie is SameSite=Lax. In production the frontend (Vercel) and
 * backend (Railway) are different sites, so COOKIE_SECURE=true switches it to
 * Secure + SameSite=None — otherwise the browser won't send it on API calls.
 */
@RestController
public class AuthenticationController {

	private final UserService userService;
	private final SessionService sessionService;
	private final boolean secureCookie;

	public AuthenticationController(UserService userService, SessionService sessionService,
			@Value("${app.cookie-secure:false}") boolean secureCookie) {
		this.userService = userService;
		this.sessionService = sessionService;
		this.secureCookie = secureCookie;
	}

	@PostMapping("/register")
	public ResponseEntity<User> register(@RequestBody LoginDTO request) {
		User user = userService.register(request);
		String token = sessionService.createSession(user.getUserId());
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(30)).toString())
			.body(user);
	}

	@PostMapping("/login")
	public ResponseEntity<User> login(@RequestBody LoginDTO request) {
		User user = userService.authenticate(request);
		String token = sessionService.createSession(user.getUserId());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(30)).toString())
			.body(user);
	}

	/** Returns the user attached to the current session cookie, or 401. */
	@GetMapping("/me")
	public User me(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return sessionService.requireUser(token);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessionService.invalidate(token);
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
			.build();
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(SessionService.COOKIE_NAME, value)
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite(secureCookie ? "None" : "Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
	}
}
