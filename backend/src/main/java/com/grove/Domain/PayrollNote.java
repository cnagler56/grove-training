package com.grove.Domain;

import java.math.BigDecimal;
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

/** A deduction, addition, or comment for one employee on one pay period (by check date). */
@Entity
@Table(name = "payroll_notes", indexes = {
	@Index(name = "ix_payroll_notes_check", columnList = "check_date"),
	@Index(name = "ix_payroll_notes_user", columnList = "user_id")
})
public class PayrollNote {

	public enum Kind { ADDITION, DEDUCTION, COMMENT }

	/** NEW until the person doing payroll acknowledges it; FINISHED once it's entered into payroll. */
	public enum Status { NEW, ACKNOWLEDGED, FINISHED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "check_date", nullable = false)
	private LocalDate checkDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20, nullable = false)
	private Kind kind;

	/** Dollars, always positive; the kind says which way. Null for a comment. */
	@Column(precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(length = 500)
	private String note;

	/** Null on rows from before statuses existed; treated as NEW. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private Status status = Status.NEW;

	@Column(name = "acknowledged_by")
	private Long acknowledgedBy;

	@Column(name = "acknowledged_at")
	private LocalDateTime acknowledgedAt;

	@Column(name = "finished_by")
	private Long finishedBy;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Long getId() { return id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public LocalDate getCheckDate() { return checkDate; }
	public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }

	public Kind getKind() { return kind; }
	public void setKind(Kind kind) { this.kind = kind; }

	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal amount) { this.amount = amount; }

	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }

	public Status getStatus() { return status == null ? Status.NEW : status; }
	public void setStatus(Status status) { this.status = status; }

	public Long getAcknowledgedBy() { return acknowledgedBy; }
	public void setAcknowledgedBy(Long acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

	public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
	public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

	public Long getFinishedBy() { return finishedBy; }
	public void setFinishedBy(Long finishedBy) { this.finishedBy = finishedBy; }

	public LocalDateTime getFinishedAt() { return finishedAt; }
	public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public Long getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
