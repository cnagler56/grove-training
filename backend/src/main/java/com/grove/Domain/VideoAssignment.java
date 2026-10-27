package com.grove.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One person a video is assigned to, for videos shown only to assigned people. */
@Entity
@Table(name = "video_assignments",
	uniqueConstraints = @UniqueConstraint(name = "uk_video_assignment", columnNames = {"video_id", "user_id"}))
public class VideoAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "video_id", nullable = false)
	private Long videoId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	public VideoAssignment() {}

	public VideoAssignment(Long videoId, Long userId) {
		this.videoId = videoId;
		this.userId = userId;
	}

	public Long getVideoId() { return videoId; }
	public Long getUserId() { return userId; }
}
