package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A day no one can ask for off (holidays, busy events). Set on the time off calendar. */
@Entity
@Table(name = "blackout_dates")
public class BlackoutDate {

	@Id
	@Column(name = "blackout_date")
	private LocalDate date;

	/** Why, shown to staff: "Holiday rush". */
	@Column(length = 200)
	private String note;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public BlackoutDate() {}

	public BlackoutDate(LocalDate date, String note, Long createdBy, LocalDateTime createdAt) {
		this.date = date;
		this.note = note;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public LocalDate getDate() { return date; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public Long getCreatedBy() { return createdBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
