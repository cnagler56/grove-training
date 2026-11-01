package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A comment on a Team board post. */
@Entity
@Table(name = "team_post_comments", indexes = @Index(name = "ix_team_post_comment_post", columnList = "post_id"))
public class TeamPostComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public TeamPostComment() {}

	public TeamPostComment(Long postId, Long authorId, String body, LocalDateTime createdAt) {
		this.postId = postId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public Long getId() { return id; }
	public Long getPostId() { return postId; }
	public Long getAuthorId() { return authorId; }
	public String getBody() { return body; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
