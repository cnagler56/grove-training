package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One employee marking one video as watched. */
@Entity
@Table(name = "video_completions",
	uniqueConstraints = @UniqueConstraint(name = "uk_completion_user_video", columnNames = {"user_id", "video_id"}))
public class VideoCompletion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "video_id", nullable = false)
	private Long videoId;

	@Column(name = "completed_at", nullable = false)
	private LocalDateTime completedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public Long getVideoId() { return videoId; }
	public void setVideoId(Long videoId) { this.videoId = videoId; }

	public LocalDateTime getCompletedAt() { return completedAt; }
	public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
