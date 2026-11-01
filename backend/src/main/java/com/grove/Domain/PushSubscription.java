package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One phone or browser that turned on notifications for a person. */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "ix_push_user", columnList = "user_id"))
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** The push service's address for this device. */
	@Column(nullable = false, unique = true, length = 700)
	private String endpoint;

	@Column(nullable = false, length = 200)
	private String p256dh;

	@Column(nullable = false, length = 100)
	private String auth;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Long getId() { return id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public String getEndpoint() { return endpoint; }
	public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

	public String getP256dh() { return p256dh; }
	public void setP256dh(String p256dh) { this.p256dh = p256dh; }

	public String getAuth() { return auth; }
	public void setAuth(String auth) { this.auth = auth; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
