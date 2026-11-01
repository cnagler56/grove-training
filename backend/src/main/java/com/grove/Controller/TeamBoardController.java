package com.grove.Controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.TeamCommentInput;
import com.grove.Domain.Dtos.TeamFeed;
import com.grove.Domain.Dtos.TeamPinInput;
import com.grove.Domain.Dtos.TeamPostInput;
import com.grove.Service.SessionService;
import com.grove.Service.TeamBoardService;

/** The Team board on the staff Home page. */
@RestController
@RequestMapping("/api/team-board")
public class TeamBoardController {

	private final TeamBoardService board;
	private final SessionService sessions;

	public TeamBoardController(TeamBoardService board, SessionService sessions) {
		this.board = board;
		this.sessions = sessions;
	}

	@GetMapping
	public TeamFeed feed(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam(defaultValue = "0") int page) {
		return board.feed(sessions.requireUser(token), page);
	}

	@PostMapping
	public ResponseEntity<Map<String, Long>> create(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody TeamPostInput input) {
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", board.create(sessions.requireUser(token), input)));
	}

	/** One photo at a time, as a JPEG body. */
	@PostMapping(path = "/{id}/images", consumes = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Void> addImage(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody byte[] data) {
		board.addImage(sessions.requireUser(token), id, data);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@GetMapping("/images/{imageId}")
	public ResponseEntity<byte[]> image(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long imageId) {
		byte[] data = board.image(sessions.requireUser(token), imageId);
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
			.header("X-Content-Type-Options", "nosniff")
			.body(data);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		board.delete(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/pinned")
	public ResponseEntity<Void> pin(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody TeamPinInput input) {
		board.setPinned(sessions.requireUser(token), id, input.pinned());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/comments")
	public ResponseEntity<Void> comment(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody TeamCommentInput input) {
		board.comment(sessions.requireUser(token), id, input.body());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@DeleteMapping("/comments/{commentId}")
	public ResponseEntity<Void> deleteComment(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long commentId) {
		board.deleteComment(sessions.requireUser(token), commentId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/like")
	public ResponseEntity<Void> like(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		board.setLiked(sessions.requireUser(token), id, true);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/like")
	public ResponseEntity<Void> unlike(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		board.setLiked(sessions.requireUser(token), id, false);
		return ResponseEntity.noContent().build();
	}
}
