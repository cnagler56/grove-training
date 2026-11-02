package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One to-do on one person's list. Assigning to several people makes one row each,
 * so everyone checks off their own. The text is copied here, so removing it from
 * the saved items doesn't change lists it's already on.
 */
@Entity
@Table(name = "todo_assignments", indexes = {
	@Index(name = "ix_todo_assignee", columnList = "assignee_id"),
	@Index(name = "ix_todo_assigned_by", columnList = "assigned_by"),
})
public class TodoAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_text", nullable = false, length = 200)
	private String text;

	@Column(name = "assignee_id", nullable = false)
	private Long assigneeId;

	@Column(name = "assigned_by", nullable = false)
	private Long assignedBy;

	@Column(name = "assigned_at", nullable = false)
	private LocalDateTime assignedAt;

	/** Optional. */
	@Column(name = "due_date")
	private LocalDate dueDate;

	/** Null until the assignee marks it done. */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public Long getId() { return id; }

	public String getText() { return text; }
	public void setText(String text) { this.text = text; }

	public Long getAssigneeId() { return assigneeId; }
	public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

	public Long getAssignedBy() { return assignedBy; }
	public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }

	public LocalDateTime getAssignedAt() { return assignedAt; }
	public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

	public LocalDate getDueDate() { return dueDate; }
	public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

	public LocalDateTime getCompletedAt() { return completedAt; }
	public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
