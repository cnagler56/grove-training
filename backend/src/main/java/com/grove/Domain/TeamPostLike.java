package com.grove.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One person liking one Team board post. */
@Entity
@Table(name = "team_post_likes",
	uniqueConstraints = @UniqueConstraint(name = "uk_team_post_like", columnNames = {"post_id", "user_id"}))
public class TeamPostLike {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	public TeamPostLike() {}

	public TeamPostLike(Long postId, Long userId) {
		this.postId = postId;
		this.userId = userId;
	}

	public Long getPostId() { return postId; }
	public Long getUserId() { return userId; }
}
