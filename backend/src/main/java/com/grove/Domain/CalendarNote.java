package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A note on one day of the time off calendar ("Staff meeting 9am"). Seen by people who approve time off. */
@Entity
@Table(name = "calendar_notes")
public class CalendarNote {

	@Id
	@Column(name = "note_date")
	private LocalDate date;

	@Column(name = "note_text", length = 500, nullable = false)
	private String text;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public CalendarNote() {}

	public CalendarNote(LocalDate date, String text, Long updatedBy, LocalDateTime updatedAt) {
		this.date = date;
		this.text = text;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	public LocalDate getDate() { return date; }
	public String getText() { return text; }
	public void setText(String text) { this.text = text; }
	public Long getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
