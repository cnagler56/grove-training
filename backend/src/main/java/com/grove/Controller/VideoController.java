package com.grove.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.VideoDetail;
import com.grove.Domain.Dtos.VideoSummary;
import com.grove.Service.SessionService;
import com.grove.Service.VideoService;

/** The training library, for any signed-in employee. */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

	private final VideoService videos;
	private final SessionService sessions;

	public VideoController(VideoService videos, SessionService sessions) {
		this.videos = videos;
		this.sessions = sessions;
	}

	@GetMapping
	public List<VideoSummary> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return videos.list(sessions.requireUser(token));
	}

	@GetMapping("/{id}")
	public VideoDetail detail(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return videos.detail(sessions.requireUser(token), id);
	}

	/** Mark this video as watched by the signed-in employee. */
	@PutMapping("/{id}/completed")
	public ResponseEntity<Void> complete(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		videos.setCompleted(sessions.requireUser(token), id, true);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/completed")
	public ResponseEntity<Void> uncomplete(@PathVariable Long id,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		videos.setCompleted(sessions.requireUser(token), id, false);
		return ResponseEntity.noContent().build();
	}
}
