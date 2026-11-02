package com.grove.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A number the business calls: a vendor, the landlord, a repair person (the Contacts page, managers and up). */
@Entity
@Table(name = "business_contacts")
public class BusinessContact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	/** Always saved as 555-555-0100. */
	@Column(nullable = false, length = 30)
	private String phone;

	/** Who they are: vendor, landlord, plumber... */
	@Column(name = "contact_type", length = 120)
	private String type;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	public Long getId() { return id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
