package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalTime;

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
import jakarta.persistence.UniqueConstraint;

/**
 * One person's entry for one day: a working shift, or RO (they requested the
 * day off, which has no times). At most one entry per person per day.
 */
@Entity
@Table(name = "shifts",
	uniqueConstraints = @UniqueConstraint(name = "uk_shift_user_date", columnNames = {"user_id", "shift_date"}),
	indexes = @Index(name = "idx_shift_date", columnList = "shift_date"))
public class Shift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "shift_date", nullable = false)
	private LocalDate date;

	/** Null on rows from before RO existed, which are all working shifts; see {@link #getType()}. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private ShiftType type;

	/** Null for a requested-off day. */
	@Column(name = "start_time")
	private LocalTime start;

	@Column(name = "end_time")
	private LocalTime end;

	/** Optional, e.g. "Front desk" or "Training". */
	@Column(length = 100)
	private String note;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public ShiftType getType() { return type == null ? ShiftType.WORK : type; }
	public void setType(ShiftType type) { this.type = type; }

	public boolean isRequestedOff() { return getType() == ShiftType.REQUESTED_OFF; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public LocalDate getDate() { return date; }
	public void setDate(LocalDate date) { this.date = date; }

	public LocalTime getStart() { return start; }
	public void setStart(LocalTime start) { this.start = start; }

	public LocalTime getEnd() { return end; }
	public void setEnd(LocalTime end) { this.end = end; }

	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
}
