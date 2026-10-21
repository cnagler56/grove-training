package com.grove.Controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.ApplicationRequest;
import com.grove.Domain.Dtos.ApplicationUpdate;
import com.grove.Domain.JobApplication;
import com.grove.Service.ApplicationService;
import com.grove.Service.SessionService;

/** Public job-application form + the owner's view of applicants. */
@RestController
public class ApplicationController {

	private final ApplicationService applications;
	private final SessionService sessions;

	public ApplicationController(ApplicationService applications, SessionService sessions) {
		this.applications = applications;
		this.sessions = sessions;
	}

	/** Positions for the form's dropdown. Public. */
	@GetMapping("/api/apply/positions")
	public List<String> positions() {
		return ApplicationService.POSITIONS;
	}

	/** Submit an application. Public, no sign-in. */
	@PostMapping("/api/apply")
	public ResponseEntity<Void> apply(@RequestBody ApplicationRequest request, HttpServletRequest http) {
		applications.submit(request, clientKey(http));
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	// ---- Owner only ----

	@GetMapping("/api/owner/applications")
	public List<JobApplication> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return applications.list();
	}

	/** How many applications are still marked New — the badge on the owner's Applicants tab. */
	@GetMapping("/api/owner/applications/new-count")
	public long newCount(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return applications.countNew();
	}

	@GetMapping("/api/owner/applications/{id}")
	public JobApplication get(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return applications.get(id);
	}

	@PutMapping("/api/owner/applications/{id}")
	public JobApplication update(@PathVariable Long id, @RequestBody ApplicationUpdate update,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return applications.update(id, update);
	}

	@DeleteMapping("/api/owner/applications/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		applications.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** Who's submitting, for the rate limit. Behind Railway's proxy the real address is in X-Forwarded-For. */
	private static String clientKey(HttpServletRequest http) {
		String forwarded = http.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
		return http.getRemoteAddr();
	}
}
