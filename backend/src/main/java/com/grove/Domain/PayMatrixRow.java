package com.grove.Domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One step of the pay matrix: at {@code pph} production per hour or more, the
 * hourly rate is {@code rate}. Loaded by the owner from the payroll skill's
 * matrix.json; kept in the database because the code repository is public.
 */
@Entity
@Table(name = "pay_matrix")
public class PayMatrixRow {

	@Id
	@Column(precision = 8, scale = 2)
	private BigDecimal pph;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal rate;

	public PayMatrixRow() {}

	public PayMatrixRow(BigDecimal pph, BigDecimal rate) {
		this.pph = pph;
		this.rate = rate;
	}

	public BigDecimal getPph() { return pph; }
	public BigDecimal getRate() { return rate; }
}
