package com.grove.Domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One employee's numbers for one Saturday-to-Friday week, pulled from Salon
 * Ultimate's "Calculate Gross Pay" report. Keyed by the name Salon Ultimate
 * shows ("Ava Stone"); a site account is linked through
 * {@link User#getSalonUltimateName()}. Rows are kept forever so weeks can be compared.
 */
@Entity
@Table(name = "weekly_stats",
	uniqueConstraints = @UniqueConstraint(name = "uk_weekly_stat_week_name", columnNames = {"week_start", "su_name"}),
	indexes = @Index(name = "ix_weekly_stat_name", columnList = "su_name"))
public class WeeklyStat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The Saturday the week starts. */
	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	@Column(name = "su_name", nullable = false, length = 120)
	private String suName;

	/** Salon Ultimate's payroll ID (usually a number, sometimes text). */
	@Column(name = "su_payroll_id", length = 40)
	private String suPayrollId;

	@Column(name = "floor_hours", precision = 8, scale = 2)
	private BigDecimal floorHours;

	@Column(name = "off_floor_hours", precision = 8, scale = 2)
	private BigDecimal offFloorHours;

	/** Tips for the week ("Unpaid Tips"). Only ever shown to the employee themselves. */
	@Column(precision = 10, scale = 2)
	private BigDecimal tips;

	/** Service sales before discounts. */
	@Column(name = "service_sales", precision = 10, scale = 2)
	private BigDecimal serviceSales;

	/** Service sales after discounts. */
	@Column(name = "net_service_sales", precision = 10, scale = 2)
	private BigDecimal netServiceSales;

	@Column(name = "retail_sales", precision = 10, scale = 2)
	private BigDecimal retailSales;

	@Column(name = "product_per_guest", precision = 8, scale = 2)
	private BigDecimal productPerGuest;

	@Column(name = "imported_at")
	private LocalDateTime importedAt;

	public Long getId() { return id; }

	public LocalDate getWeekStart() { return weekStart; }
	public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

	public String getSuName() { return suName; }
	public void setSuName(String suName) { this.suName = suName; }

	public String getSuPayrollId() { return suPayrollId; }
	public void setSuPayrollId(String suPayrollId) { this.suPayrollId = suPayrollId; }

	public BigDecimal getFloorHours() { return floorHours; }
	public void setFloorHours(BigDecimal floorHours) { this.floorHours = floorHours; }

	public BigDecimal getOffFloorHours() { return offFloorHours; }
	public void setOffFloorHours(BigDecimal offFloorHours) { this.offFloorHours = offFloorHours; }

	public BigDecimal getTips() { return tips; }
	public void setTips(BigDecimal tips) { this.tips = tips; }

	public BigDecimal getServiceSales() { return serviceSales; }
	public void setServiceSales(BigDecimal serviceSales) { this.serviceSales = serviceSales; }

	public BigDecimal getNetServiceSales() { return netServiceSales; }
	public void setNetServiceSales(BigDecimal netServiceSales) { this.netServiceSales = netServiceSales; }

	public BigDecimal getRetailSales() { return retailSales; }
	public void setRetailSales(BigDecimal retailSales) { this.retailSales = retailSales; }

	public BigDecimal getProductPerGuest() { return productPerGuest; }
	public void setProductPerGuest(BigDecimal productPerGuest) { this.productPerGuest = productPerGuest; }

	public LocalDateTime getImportedAt() { return importedAt; }
	public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
