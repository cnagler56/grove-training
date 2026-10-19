package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A training video. The file itself lives in Bunny Stream; this row holds the
 * Bunny video GUID plus the title/category the team browses by. Playback URLs
 * are signed on demand (see BunnyStreamService), never stored.
 */
@Entity
@Table(name = "videos")
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(length = 4000)
	private String description;

	/** Free-text grouping on the library page, e.g. "Color", "Cuts", "Customer service". */
	private String category;

	@Column(name = "bunny_video_id", length = 64, unique = true)
	private String bunnyVideoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VideoStatus status = VideoStatus.UPLOADING;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getBunnyVideoId() { return bunnyVideoId; }
	public void setBunnyVideoId(String bunnyVideoId) { this.bunnyVideoId = bunnyVideoId; }

	public VideoStatus getStatus() { return status; }
	public void setStatus(VideoStatus status) { this.status = status; }

	public Integer getDurationSeconds() { return durationSeconds; }
	public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
