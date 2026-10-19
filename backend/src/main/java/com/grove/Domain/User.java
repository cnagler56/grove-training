package com.grove.Domain;

import java.time.LocalDateTime;

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
	private Role roles = Role.USER;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

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

	@JsonIgnore
	public boolean isAdmin() { return roles == Role.ADMIN; }
}
