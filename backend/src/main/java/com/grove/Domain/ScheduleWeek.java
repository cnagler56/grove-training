package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Whether a schedule week (Saturday through Friday) has been posted for staff.
 * Until it's posted, only owners and general managers can see its shifts.
 */
@Entity
@Table(name = "schedule_weeks")
public class ScheduleWeek {

	/** Always a Saturday. */
	@Id
	@Column(name = "week_start")
	private LocalDate weekStart;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// Approval: a builder sends the week to one approver. All null when not pending.
	@Column(name = "submitted_by")
	private Long submittedBy;

	@Column(name = "submitted_to")
	private Long submittedTo;

	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	/** Why an approver sent the week back; cleared when it's sent again or posted. */
	@Column(name = "return_note", length = 500)
	private String returnNote;

	@Column(name = "returned_by")
	private Long returnedBy;

	/** Sent for approval and waiting on the approver. */
	public boolean isPending() { return !published && submittedTo != null; }

	public Long getSubmittedBy() { return submittedBy; }
	public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }

	public Long getSubmittedTo() { return submittedTo; }
	public void setSubmittedTo(Long submittedTo) { this.submittedTo = submittedTo; }

	public LocalDateTime getSubmittedAt() { return submittedAt; }
	public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

	public String getReturnNote() { return returnNote; }
	public void setReturnNote(String returnNote) { this.returnNote = returnNote; }

	public Long getReturnedBy() { return returnedBy; }
	public void setReturnedBy(Long returnedBy) { this.returnedBy = returnedBy; }

	/** Back to a plain draft: no pending approval. Keeps any send-back note unless told otherwise. */
	public void clearSubmission() {
		submittedBy = null;
		submittedTo = null;
		submittedAt = null;
	}

	public LocalDate getWeekStart() { return weekStart; }
	public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

	public boolean isPublished() { return published; }
	public void setPublished(boolean published) { this.published = published; }

	public LocalDateTime getPublishedAt() { return publishedAt; }
	public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
