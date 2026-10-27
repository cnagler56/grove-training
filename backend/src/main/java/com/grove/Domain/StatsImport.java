package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The last attempt to pull one week's numbers from Salon Ultimate, and how it went. */
@Entity
@Table(name = "stats_imports")
public class StatsImport {

	public enum Status { OK, BLOCKED, FAILED, NOT_CONFIGURED }

	@Id
	@Column(name = "week_start")
	private LocalDate weekStart;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20, nullable = false)
	private Status status;

	/** What went wrong (or which timecards are open); null when OK. */
	@Column(length = 1000)
	private String message;

	@Column(name = "row_count")
	private Integer rowCount;

	@Column(name = "attempted_at", nullable = false)
	private LocalDateTime attemptedAt;

	public StatsImport() {}

	public StatsImport(LocalDate weekStart, Status status, String message, Integer rowCount, LocalDateTime attemptedAt) {
		this.weekStart = weekStart;
		this.status = status;
		this.message = message;
		this.rowCount = rowCount;
		this.attemptedAt = attemptedAt;
	}

	public LocalDate getWeekStart() { return weekStart; }
	public Status getStatus() { return status; }
	public String getMessage() { return message; }
	public Integer getRowCount() { return rowCount; }
	public LocalDateTime getAttemptedAt() { return attemptedAt; }
}
