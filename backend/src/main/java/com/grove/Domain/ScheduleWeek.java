package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Whether a schedule week (Saturday through Friday) has been posted for staff.
 * Until it's posted, only owners and general managers can see its shifts.
 */
@Entity
@Table(name = "schedule_weeks")
public class ScheduleWeek {

	/** Always a Saturday. */
	@Id
	@Column(name = "week_start")
	private LocalDate weekStart;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public LocalDate getWeekStart() { return weekStart; }
	public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

	public boolean isPublished() { return published; }
	public void setPublished(boolean published) { this.published = published; }

	public LocalDateTime getPublishedAt() { return publishedAt; }
	public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
