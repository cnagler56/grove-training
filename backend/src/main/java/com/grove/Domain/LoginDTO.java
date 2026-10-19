package com.grove.Domain;

/**
 * Credentials posted to /login, plus the identity fields and team code posted
 * to /register.
 */
public class LoginDTO {

	private String email;
	private String password;
	private String firstName;
	private String lastName;
	private String teamCode;

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }

	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }

	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }

	public String getTeamCode() { return teamCode; }
	public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
}
