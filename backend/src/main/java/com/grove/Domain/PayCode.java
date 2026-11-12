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

	/**
	 * Which bump this person gets: null follows their role (leads and managers), or NONE,
	 * LEAD, LEAD_PROD, MANAGER, or CUSTOM (the amount in {@link #bump}).
	 */
	@Column(name = "bump_kind", length = 20)
	private String bumpKind;

	/** Their own bump per hour, used when bumpKind is CUSTOM. */
	@Column(precision = 8, scale = 2)
	private BigDecimal bump;

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
	public void setFlatRate(BigDecimal flatRate) { this.flatRate = flatRate; }
	public String getBumpKind() { return bumpKind; }
	public void setBumpKind(String bumpKind) { this.bumpKind = bumpKind; }
	public BigDecimal getBump() { return bump; }
	public void setBump(BigDecimal bump) { this.bump = bump; }

	/** True when this row holds something the owner set: their own base rate or bump. */
	public boolean isPersonal() { return flatRate != null || bumpKind != null; }
}
