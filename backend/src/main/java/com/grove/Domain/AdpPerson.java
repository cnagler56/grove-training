package com.grove.Domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What the ADP import file needs to know about one person, set by the owner on Settings.
 * Keyed by their name as Salon Ultimate shows it ("Ava Stone"), like pay_codes. Real
 * employee IDs live only in the database (the code repository is public).
 */
@Entity
@Table(name = "adp_people")
public class AdpPerson {

	@Id
	@Column(name = "su_name", length = 120)
	private String suName;

	/** Their employee ID in ADP RUN (the Employee Codes report). */
	@Column(name = "employee_id", length = 15)
	private String employeeId;

	/** ADP pays them a fixed amount; the file sends their hours only, for the record. */
	private Boolean salaried;

	/** Added as a bonus on the last check of every month; null for none. */
	@Column(name = "monthly_bonus", precision = 8, scale = 2)
	private BigDecimal monthlyBonus;

	public AdpPerson() {}

	public AdpPerson(String suName) {
		this.suName = suName;
	}

	public String getSuName() { return suName; }
	public String getEmployeeId() { return employeeId; }
	public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
	public boolean isSalaried() { return Boolean.TRUE.equals(salaried); }
	public void setSalaried(boolean salaried) { this.salaried = salaried ? Boolean.TRUE : null; }
	public BigDecimal getMonthlyBonus() { return monthlyBonus; }
	public void setMonthlyBonus(BigDecimal monthlyBonus) { this.monthlyBonus = monthlyBonus; }

	/** True when nothing is set and the row can go. */
	public boolean isEmpty() { return employeeId == null && !isSalaried() && monthlyBonus == null; }
}
