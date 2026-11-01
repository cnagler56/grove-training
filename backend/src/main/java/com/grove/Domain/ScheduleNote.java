package com.grove.Domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A private note about one employee for one week, written by whoever builds the
 * schedule. Only people who can build or approve the schedule ever receive it.
 */
@Entity
@Table(name = "schedule_notes",
	uniqueConstraints = @UniqueConstraint(name = "uk_schedule_note_week_user", columnNames = {"week_start", "user_id"}))
public class ScheduleNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 500)
	private String note;

	public ScheduleNote() {}

	public ScheduleNote(LocalDate weekStart, Long userId, String note) {
		this.weekStart = weekStart;
		this.userId = userId;
		this.note = note;
	}

	public LocalDate getWeekStart() { return weekStart; }
	public Long getUserId() { return userId; }
	public String getNote() { return note; }
}
