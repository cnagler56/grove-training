package com.grove.Domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	/**
	 * Where the video plays from. Null on rows from before YouTube links existed,
	 * which are all Bunny uploads; {@link #getSource()} reads null as BUNNY, so
	 * old rows need no backfill.
	 */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private VideoSource source;

	/** The 11-character YouTube video id, for YOUTUBE videos. */
	@Column(name = "youtube_id", length = 20)
	private String youtubeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VideoStatus status = VideoStatus.UPLOADING;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	/** True: only the people in video_assignments (and managers) see it. Null/false: everyone on staff. */
	@Column(name = "assigned_only")
	private Boolean assignedOnly;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public VideoSource getSource() { return source == null ? VideoSource.BUNNY : source; }
	public void setSource(VideoSource source) { this.source = source; }

	public String getYoutubeId() { return youtubeId; }
	public void setYoutubeId(String youtubeId) { this.youtubeId = youtubeId; }

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

	public boolean isAssignedOnly() { return Boolean.TRUE.equals(assignedOnly); }
	public void setAssignedOnly(boolean assignedOnly) { this.assignedOnly = assignedOnly; }
}
