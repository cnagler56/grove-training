package com.grove.Domain;

import java.time.LocalDate;
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
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** An employee asking for days off. Once approved, those days show as RO on the schedule. */
@Entity
@Table(name = "time_off_requests", indexes = @Index(name = "ix_time_off_user", columnList = "user_id"))
public class TimeOffRequest {

	public enum Status { PENDING, APPROVED, SENT_BACK }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Last day off, inclusive. */
	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	/** The employee's reason, if they gave one. */
	@Column(length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20, nullable = false)
	private Status status = Status.PENDING;

	/** The approver's message back to the employee. */
	@Column(name = "response_note", length = 500)
	private String responseNote;

	@Column(name = "decided_by")
	private Long decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Long getId() { return id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }

	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }

	public String getResponseNote() { return responseNote; }
	public void setResponseNote(String responseNote) { this.responseNote = responseNote; }

	public Long getDecidedBy() { return decidedBy; }
	public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }

	public LocalDateTime getDecidedAt() { return decidedAt; }
	public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
