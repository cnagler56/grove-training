package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One person working one day. At most one shift per person per day. */
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

	@Column(name = "start_time", nullable = false)
	private LocalTime start;

	@Column(name = "end_time", nullable = false)
	private LocalTime end;

	/** Optional, e.g. "Front desk" or "Training". */
	@Column(length = 100)
	private String note;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

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
