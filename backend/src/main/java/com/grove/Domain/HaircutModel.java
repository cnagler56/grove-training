package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Someone who'll sit for a haircut so stylists can practice or train (the Models page, managers and up). */
@Entity
@Table(name = "haircut_models")
public class HaircutModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	/** Where we found them: Instagram, a walk-in, a friend of a stylist... */
	@Column(length = 120)
	private String source;

	@Column(length = 30)
	private String phone;

	@Column(length = 2000)
	private String notes;

	@Column(name = "last_visit")
	private LocalDate lastVisit;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Long getId() { return id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }

	public LocalDate getLastVisit() { return lastVisit; }
	public void setLastVisit(LocalDate lastVisit) { this.lastVisit = lastVisit; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
