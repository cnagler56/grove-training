package com.grove.Domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Someone's pay code from the payroll skill's employee_codes.json: a premium
 * added to the matrix rate, or a flat hourly rate instead of the matrix.
 * Keyed by their name as Salon Ultimate shows it ("Ava Stone").
 */
@Entity
@Table(name = "pay_codes")
public class PayCode {

	@Id
	@Column(name = "su_name", length = 120)
	private String suName;

	private Integer code;

	@Column(precision = 8, scale = 2)
	private BigDecimal premium;

	/** Replaces the matrix entirely when set. */
	@Column(name = "flat_rate", precision = 8, scale = 2)
	private BigDecimal flatRate;

	public PayCode() {}

	public PayCode(String suName, Integer code, BigDecimal premium, BigDecimal flatRate) {
		this.suName = suName;
		this.code = code;
		this.premium = premium;
		this.flatRate = flatRate;
	}

	public String getSuName() { return suName; }
	public Integer getCode() { return code; }
	public BigDecimal getPremium() { return premium; }
	public BigDecimal getFlatRate() { return flatRate; }
}
