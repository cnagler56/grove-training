package com.grove.Domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A job application sent from the public /apply page. Only owners can see
 * these (they contain applicants' personal details).
 */
@Entity
@Table(name = "job_applications")
public class JobApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, length = 30)
	private String phone;

	/** Position applied for: one of ApplicationService.POSITIONS. */
	@Column(nullable = false, length = 40)
	private String position;

	private Boolean licensed;

	@Column(name = "license_number", length = 60)
	private String licenseNumber;

	@Column(name = "years_experience")
	private Integer yearsExperience;

	@Column(length = 1000)
	private String availability;

	/** Instagram handle or portfolio link. */
	@Column(length = 300)
	private String portfolio;

	@Column(length = 4000)
	private String message;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ApplicationStatus status = ApplicationStatus.NEW;

	/** Private notes from the owner (interview impressions, etc.). */
	@Column(name = "owner_notes", length = 4000)
	private String ownerNotes;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }

	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public String getPosition() { return position; }
	public void setPosition(String position) { this.position = position; }

	public Boolean getLicensed() { return licensed; }
	public void setLicensed(Boolean licensed) { this.licensed = licensed; }

	public String getLicenseNumber() { return licenseNumber; }
	public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

	public Integer getYearsExperience() { return yearsExperience; }
	public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

	public String getAvailability() { return availability; }
	public void setAvailability(String availability) { this.availability = availability; }

	public String getPortfolio() { return portfolio; }
	public void setPortfolio(String portfolio) { this.portfolio = portfolio; }

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public ApplicationStatus getStatus() { return status; }
	public void setStatus(ApplicationStatus status) { this.status = status; }

	public String getOwnerNotes() { return ownerNotes; }
	public void setOwnerNotes(String ownerNotes) { this.ownerNotes = ownerNotes; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
