package com.grove.Domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The manager on duty (MOD) for one day of the schedule. Saved with the rest of
 * the week in the schedule editor; at most one per day. Only people the owner
 * allows on the Settings page can be picked.
 */
@Entity
@Table(name = "managers_on_duty")
public class ManagerOnDuty {

	@Id
	@Column(name = "mod_date")
	private LocalDate date;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	public ManagerOnDuty() {}

	public ManagerOnDuty(LocalDate date, Long userId) {
		this.date = date;
		this.userId = userId;
	}

	public LocalDate getDate() { return date; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
}
