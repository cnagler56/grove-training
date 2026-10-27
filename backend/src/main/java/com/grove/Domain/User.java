package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An employee (USER) or manager (ADMIN). The password is stored BCrypt-hashed
 * and never serialized. {@code active=false} is how a manager removes someone
 * who has left: they can no longer sign in, and their existing sessions stop
 * resolving, but their watch history is kept.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "userid")
	private Long userId;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(unique = true, nullable = false)
	private String email;

	@Column(name = "password", nullable = false)
	private String password;

	@Enumerated(EnumType.STRING)
	// VARCHAR rather than a native MySQL ENUM, so adding a role later needs no ALTER TABLE.
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30, nullable = false)
	private Role roles = Role.STYLIST;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	// Employee details a manager keeps on file. All optional (nullable objects,
	// so adding them under ddl-auto=update needs no backfill).
	@Column(length = 30)
	private String phone;

	@Column(name = "hire_date")
	private LocalDate hireDate;

	private LocalDate birthday;

	/** When a manager disabled the account (the employee left); null while active. */
	@Column(name = "disabled_at")
	private LocalDateTime disabledAt;

	/** Set while someone is taking time off (leave, a break): they stay signed in but are left off the schedule. Null = active. */
	@Column(name = "inactive_since")
	private LocalDate inactiveSince;

	/** Their name as Salon Ultimate shows it ("Ava Stone"), linking them to their weekly numbers. */
	@Column(name = "salon_ultimate_name", length = 120)
	private String salonUltimateName;

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }

	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	@JsonIgnore
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

	public Role getRoles() { return roles; }
	public void setRoles(Role roles) { this.roles = roles; }

	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public LocalDate getHireDate() { return hireDate; }
	public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

	public LocalDate getBirthday() { return birthday; }
	public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

	public LocalDateTime getDisabledAt() { return disabledAt; }
	public void setDisabledAt(LocalDateTime disabledAt) { this.disabledAt = disabledAt; }

	public LocalDate getInactiveSince() { return inactiveSince; }
	public void setInactiveSince(LocalDate inactiveSince) { this.inactiveSince = inactiveSince; }

	public String getSalonUltimateName() { return salonUltimateName; }
	public void setSalonUltimateName(String salonUltimateName) { this.salonUltimateName = salonUltimateName; }

	@JsonIgnore
	public boolean isAdmin() { return roles.canManageSite(); }
}
