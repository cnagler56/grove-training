package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A copy of a notification the app sent someone, for the Notifications list. Saved
 * whether or not they've turned on phone notifications, so nothing is missed.
 */
@Entity
@Table(name = "user_notifications", indexes = @Index(name = "ix_user_notification_user", columnList = "user_id, created_at"))
public class UserNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 500)
	private String body;

	/** The page it opens, like "/time-off". */
	@Column(nullable = false, length = 200)
	private String url;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** Null until they open it, or the page it points to. */
	@Column(name = "read_at")
	private LocalDateTime readAt;

	public Long getId() { return id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getBody() { return body; }
	public void setBody(String body) { this.body = body; }

	public String getUrl() { return url; }
	public void setUrl(String url) { this.url = url; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getReadAt() { return readAt; }
	public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
