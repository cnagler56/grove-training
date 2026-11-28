package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A day marked on the time off calendar: the salon is closed (a one-time closure, or a
 * holiday with its own reason), or open on a holiday it usually closes for.
 */
@Entity
@Table(name = "closed_days")
public class ClosedDay {

	@Id
	@Column(name = "closed_date")
	private LocalDate date;

	/** True: closed. False: open this year on a holiday that's usually closed. */
	@Column(name = "salon_closed")
	private Boolean closed;

	/** Why, like "Snow day". */
	@Column(length = 200)
	private String note;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public ClosedDay() {}

	public ClosedDay(LocalDate date, Boolean closed, String note, Long createdBy, LocalDateTime createdAt) {
		this.date = date;
		this.closed = closed;
		this.note = note;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public LocalDate getDate() { return date; }
	public boolean isClosed() { return !Boolean.FALSE.equals(closed); }
	public void setClosed(Boolean closed) { this.closed = closed; }
	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }
	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
