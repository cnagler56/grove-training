package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A post on the Team board (staff Home page). */
@Entity
@Table(name = "team_posts")
public class TeamPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(length = 5000)
	private String body;

	/** Pinned posts stay at the top. Null/false: not pinned. */
	private Boolean pinned;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Long getId() { return id; }

	public Long getAuthorId() { return authorId; }
	public void setAuthorId(Long authorId) { this.authorId = authorId; }

	public String getBody() { return body; }
	public void setBody(String body) { this.body = body; }

	public boolean isPinned() { return Boolean.TRUE.equals(pinned); }
	public void setPinned(boolean pinned) { this.pinned = pinned; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
