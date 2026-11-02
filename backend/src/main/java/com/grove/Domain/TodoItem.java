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

/** A to-do someone has assigned before, kept so it can be picked from the dropdown next time. */
@Entity
@Table(name = "todo_items")
public class TodoItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_text", nullable = false, length = 200)
	private String text;

	/** Whose dropdown it's in, so each group keeps its own list. Null (items saved before groups) shows for everyone. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "assignee_role", length = 30)
	private Role role;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Long getId() { return id; }

	public String getText() { return text; }
	public void setText(String text) { this.text = text; }

	public Role getRole() { return role; }
	public void setRole(Role role) { this.role = role; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
