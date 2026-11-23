package com.grove.Domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One day's business from Salon Ultimate, pulled 30 minutes after closing, for the
 * owner's Home page. Totals are summed across everyone who worked that day.
 */
@Entity
@Table(name = "daily_sales")
public class DailySales {

	/** OK: saved. BLOCKED: a timecard was still open. FAILED: couldn't pull. */
	public enum Status { OK, BLOCKED, FAILED }

	@Id
	@Column(name = "sales_date")
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20, nullable = false)
	private Status status;

	/** Why it isn't OK, e.g. who still has a timecard open. */
	@Column(length = 500)
	private String message;

	/** Service sales before discounts. */
	@Column(name = "service_sales", precision = 12, scale = 2)
	private BigDecimal serviceSales;

	/** Service sales after discounts. */
	@Column(name = "net_service_sales", precision = 12, scale = 2)
	private BigDecimal netServiceSales;

	@Column(name = "retail_sales", precision = 12, scale = 2)
	private BigDecimal retailSales;

	@Column(precision = 12, scale = 2)
	private BigDecimal tips;

	@Column(name = "floor_hours", precision = 10, scale = 2)
	private BigDecimal floorHours;

	private Integer clients;

	private Integer services;

	/** From the Detailed Service report, for monthly goals. Null until pulled. */
	private Integer waxes;

	private Integer treatments;

	/** How many people worked. 0 on a day the salon was closed. */
	@Column(name = "staff_worked")
	private Integer staffWorked;

	@Column(name = "attempted_at")
	private LocalDateTime attemptedAt;

	/** Set once managers have been told about an open timecard for this day. */
	@Column(name = "timecard_alert_sent")
	private Boolean timecardAlertSent;

	/** Set once the owners have been sent this day's summary. */
	@Column(name = "summary_sent")
	private Boolean summarySent;

	public DailySales() {}

	public DailySales(LocalDate date) {
		this.date = date;
	}

	public LocalDate getDate() { return date; }

	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public BigDecimal getServiceSales() { return serviceSales; }
	public void setServiceSales(BigDecimal serviceSales) { this.serviceSales = serviceSales; }

	public BigDecimal getNetServiceSales() { return netServiceSales; }
	public void setNetServiceSales(BigDecimal netServiceSales) { this.netServiceSales = netServiceSales; }

	public BigDecimal getRetailSales() { return retailSales; }
	public void setRetailSales(BigDecimal retailSales) { this.retailSales = retailSales; }

	public BigDecimal getTips() { return tips; }
	public void setTips(BigDecimal tips) { this.tips = tips; }

	public BigDecimal getFloorHours() { return floorHours; }
	public void setFloorHours(BigDecimal floorHours) { this.floorHours = floorHours; }

	public Integer getClients() { return clients; }
	public void setClients(Integer clients) { this.clients = clients; }

	public Integer getServices() { return services; }
	public void setServices(Integer services) { this.services = services; }

	public Integer getWaxes() { return waxes; }
	public void setWaxes(Integer waxes) { this.waxes = waxes; }

	public Integer getTreatments() { return treatments; }
	public void setTreatments(Integer treatments) { this.treatments = treatments; }

	public Integer getStaffWorked() { return staffWorked; }
	public void setStaffWorked(Integer staffWorked) { this.staffWorked = staffWorked; }

	public LocalDateTime getAttemptedAt() { return attemptedAt; }
	public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }

	public boolean isTimecardAlertSent() { return Boolean.TRUE.equals(timecardAlertSent); }
	public void setTimecardAlertSent(boolean sent) { this.timecardAlertSent = sent; }

	public boolean isSummarySent() { return Boolean.TRUE.equals(summarySent); }
	public void setSummarySent(boolean sent) { this.summarySent = sent; }

	/** Sales after discounts plus retail: the day's business. */
	public BigDecimal total() {
		BigDecimal net = netServiceSales == null ? BigDecimal.ZERO : netServiceSales;
		BigDecimal retail = retailSales == null ? BigDecimal.ZERO : retailSales;
		return net.add(retail);
	}

	/** Saved, and someone worked: the salon was open. */
	public boolean wasOpen() {
		return status == Status.OK && staffWorked != null && staffWorked > 0;
	}
}
