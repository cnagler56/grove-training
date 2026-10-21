package com.grove.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.ApplicationRequest;
import com.grove.Domain.Dtos.ApplicationUpdate;
import com.grove.Domain.ApplicationStatus;
import com.grove.Domain.JobApplication;
import com.grove.Repository.JobApplicationRepository;

/**
 * Job applications from the public /apply page. Anyone can submit; only
 * owners can read them.
 *
 * Spam guards: a honeypot field bots fill in (we quietly drop those), and at
 * most {@link #MAX_PER_WINDOW} submissions per connection per hour.
 */
@Service
public class ApplicationService {

	public static final List<String> POSITIONS = List.of("Stylist", "Receptionist", "Lead", "Manager", "Other");
	private static final int MAX_PER_WINDOW = 5;
	private static final Duration WINDOW = Duration.ofHours(1);

	private final JobApplicationRepository repo;
	private final ApplicationNotifier notifier;
	private final Map<String, Deque<Instant>> recentByClient = new ConcurrentHashMap<>();

	public ApplicationService(JobApplicationRepository repo, ApplicationNotifier notifier) {
		this.repo = repo;
		this.notifier = notifier;
	}

	public void submit(ApplicationRequest r, String clientKey) {
		if (r == null) throw bad("Fill in the form first");
		// Honeypot: a hidden field people never see. Pretend it worked so bots don't adapt.
		if (r.website() != null && !r.website().isBlank()) return;
		rateLimit(clientKey);

		String email = required(r.email(), "Email").toLowerCase();
		if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw bad("Enter a valid email address");
		String position = required(r.position(), "Position");
		if (!POSITIONS.contains(position)) throw bad("Pick a position from the list");
		if (r.yearsExperience() != null && (r.yearsExperience() < 0 || r.yearsExperience() > 60)) {
			throw bad("Years of experience should be between 0 and 60");
		}

		JobApplication a = new JobApplication();
		a.setFirstName(limit(required(r.firstName(), "First name"), 100, "First name"));
		a.setLastName(limit(required(r.lastName(), "Last name"), 100, "Last name"));
		a.setEmail(limit(email, 255, "Email"));
		a.setPhone(limit(required(r.phone(), "Phone"), 30, "Phone"));
		a.setPosition(position);
		a.setLicensed(r.licensed());
		a.setLicenseNumber(limit(optional(r.licenseNumber()), 60, "License number"));
		a.setYearsExperience(r.yearsExperience());
		a.setAvailability(limit(optional(r.availability()), 1000, "Availability"));
		a.setPortfolio(limit(optional(r.portfolio()), 300, "Instagram or portfolio"));
		a.setMessage(limit(optional(r.message()), 4000, "About you"));
		a.setCreatedAt(LocalDateTime.now());
		notifier.newApplication(repo.save(a));
	}

	// ---- Owner ---------------------------------------------------------------

	public List<JobApplication> list() {
		return repo.findAllByOrderByCreatedAtDesc();
	}

	public long countNew() {
		return repo.countByStatus(ApplicationStatus.NEW);
	}

	public JobApplication get(Long id) {
		return repo.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
	}

	public JobApplication update(Long id, ApplicationUpdate u) {
		JobApplication a = get(id);
		if (u.status() != null) a.setStatus(u.status());
		if (u.ownerNotes() != null) a.setOwnerNotes(limit(optional(u.ownerNotes()), 4000, "Notes"));
		return repo.save(a);
	}

	public void delete(Long id) {
		repo.delete(get(id));
	}

	// ---- helpers -------------------------------------------------------------

	private void rateLimit(String clientKey) {
		Instant now = Instant.now();
		Deque<Instant> times = recentByClient.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) times.pollFirst();
			if (times.size() >= MAX_PER_WINDOW) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Too many applications from this connection. Please try again later.");
			}
			times.addLast(now);
		}
	}

	private static String required(String s, String field) {
		if (s == null || s.isBlank()) throw bad(field + " is required");
		return s.trim();
	}

	private static String optional(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String limit(String s, int max, String field) {
		if (s != null && s.length() > max) throw bad(field + " is too long (max " + max + " characters)");
		return s;
	}

	private static ResponseStatusException bad(String msg) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}
}
