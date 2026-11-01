package com.grove.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** A JPEG on a Team board post. Kept in the database so only signed-in staff can fetch it. */
@Entity
@Table(name = "team_post_images", indexes = @Index(name = "ix_team_post_image_post", columnList = "post_id"))
public class TeamPostImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Lob
	@Column(nullable = false, length = 5 * 1024 * 1024)
	private byte[] data;

	public TeamPostImage() {}

	public TeamPostImage(Long postId, byte[] data) {
		this.postId = postId;
		this.data = data;
	}

	public Long getId() { return id; }
	public Long getPostId() { return postId; }
	public byte[] getData() { return data; }
}
