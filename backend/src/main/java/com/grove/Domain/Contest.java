package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A contest for who can do the most of one kind of service between two dates,
 * counted from Salon Ultimate. People can also be split into teams.
 */
@Entity
@Table(name = "contests")
public class Contest {

	/** What's counted, from Salon Ultimate's Detailed Service report. */
	public enum Metric {
		TREATMENTS("treatment", "treatments"),
		COLORS("color", "colors"),
		WAXES("wax", "waxes");

		private final String one;
		private final String many;

		Metric(String one, String many) {
			this.one = one;
			this.many = many;
		}

		/** "treatment" for 1, "treatments" otherwise. */
		public String word(int n) { return n == 1 ? one : many; }

		/** "1 treatment", "3 treatments". */
		public String count(int n) { return n + " " + word(n); }

		public String plural() { return many; }
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Metric metric;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(length = 200)
	private String prize;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	/** The last time Salon Ultimate was read successfully. */
	@Column(name = "last_checked_at")
	private LocalDateTime lastCheckedAt;

	/** Why the last check didn't work, shown to managers. Null when it did. */
	@Column(name = "check_problem", length = 500)
	private String checkProblem;

	/** The position of the team in the lead at the last check; null when tied or there are no teams. */
	@Column(name = "leading_team")
	private Integer leadingTeam;

	/** Set once the final standings are in and announced. */
	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	public Long getId() { return id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public Metric getMetric() { return metric; }
	public void setMetric(Metric metric) { this.metric = metric; }

	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

	public String getPrize() { return prize; }
	public void setPrize(String prize) { this.prize = prize; }

	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
	public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

	public String getCheckProblem() { return checkProblem; }
	public void setCheckProblem(String checkProblem) { this.checkProblem = checkProblem; }

	public Integer getLeadingTeam() { return leadingTeam; }
	public void setLeadingTeam(Integer leadingTeam) { this.leadingTeam = leadingTeam; }

	public LocalDateTime getFinishedAt() { return finishedAt; }
	public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
