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

import com.grove.Domain.Dtos.AdminStatus;
import com.grove.Domain.Dtos.CompletionReport;
import com.grove.Domain.Dtos.EmployeeDetails;
import com.grove.Domain.Dtos.NewEmployee;
import com.grove.Domain.Dtos.PasswordReset;
import com.grove.Domain.Dtos.TeamMember;
import com.grove.Domain.Dtos.TeamMemberUpdate;
import com.grove.Domain.Dtos.UploadTicket;
import com.grove.Domain.Dtos.VideoRequest;
import com.grove.Domain.Dtos.VideoAudience;
import com.grove.Domain.Dtos.VideoSummary;
import com.grove.Domain.Dtos.YouTubeRequest;
import com.grove.Service.BunnyStreamService;
import com.grove.Service.SessionService;
import com.grove.Service.UserService;
import com.grove.Service.VideoService;

/** Manager-only: add/edit/remove videos, see who's watched what, manage the team. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final SessionService sessions;
	private final VideoService videos;
	private final UserService users;
	private final BunnyStreamService bunny;

	public AdminController(SessionService sessions, VideoService videos, UserService users, BunnyStreamService bunny) {
		this.sessions = sessions;
		this.videos = videos;
		this.users = users;
		this.bunny = bunny;
	}

	@GetMapping("/status")
	public AdminStatus status(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return new AdminStatus(bunny.isConfigured(), users.isSignupOpen());
	}

	// ---- Videos ----

	@GetMapping("/categories")
	public List<String> categories(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.categories();
	}

	@PostMapping("/videos")
	public ResponseEntity<UploadTicket> create(@RequestBody VideoRequest req,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return ResponseEntity.status(HttpStatus.CREATED).body(videos.create(sessions.requireAdmin(token), req));
	}

	/** Add a YouTube video by link (works without Bunny set up). */
	@PostMapping("/videos/youtube")
	public ResponseEntity<VideoSummary> addYouTube(@RequestBody YouTubeRequest req,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return ResponseEntity.status(HttpStatus.CREATED).body(videos.createYouTube(sessions.requireAdmin(token), req));
	}

	@PostMapping("/videos/{id}/upload-ticket")
	public UploadTicket uploadTicket(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.uploadTicket(id);
	}

	@PutMapping("/videos/{id}")
	public VideoSummary update(@PathVariable Long id, @RequestBody VideoRequest req,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.update(id, req);
	}

	@DeleteMapping("/videos/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		videos.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** Who can see a video: everyone, or only certain people. */
	@GetMapping("/videos/{id}/audience")
	public VideoAudience audience(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.audience(id);
	}

	@PutMapping("/videos/{id}/audience")
	public VideoAudience setAudience(@PathVariable Long id, @RequestBody VideoAudience audience,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.setAudience(id, audience);
	}

	@GetMapping("/videos/{id}/completions")
	public CompletionReport completions(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return videos.completions(id);
	}

	// ---- Team ----

	@GetMapping("/team")
	public List<TeamMember> team(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return users.team();
	}

	/** Owner adds an employee directly. */
	@PostMapping("/team")
	public ResponseEntity<TeamMember> create(@RequestBody NewEmployee employee,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return ResponseEntity.status(HttpStatus.CREATED).body(users.create(employee));
	}

	/** Owner permanently deletes an account and its watch history. */
	@DeleteMapping("/team/{userId}")
	public ResponseEntity<Void> deleteMember(@PathVariable Long userId,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		users.delete(sessions.requireOwner(token), userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/team/{userId}")
	public TeamMember member(@PathVariable Long userId,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return users.member(userId);
	}

	@PutMapping("/team/{userId}/details")
	public TeamMember updateDetails(@PathVariable Long userId, @RequestBody EmployeeDetails details,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return users.updateDetails(sessions.requireAdmin(token), userId, details);
	}

	@PutMapping("/team/{userId}")
	public TeamMember updateMember(@PathVariable Long userId, @RequestBody TeamMemberUpdate change,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return users.update(sessions.requireAdmin(token), userId, change);
	}

	@PostMapping("/team/{userId}/password")
	public ResponseEntity<Void> resetPassword(@PathVariable Long userId, @RequestBody PasswordReset body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		users.resetPassword(sessions.requireAdmin(token), userId, body.password());
		return ResponseEntity.noContent().build();
	}
}
