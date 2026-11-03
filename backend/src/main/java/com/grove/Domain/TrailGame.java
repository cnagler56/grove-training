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

import com.grove.Domain.Contest.Handicap;

/**
 * The Grove Trail: an Oregon Trail style race from Maple Grove to the Oregon Coast,
 * paid for with coins from Salon Ultimate service sales. One turn per check.
 */
@Entity
@Table(name = "trail_games")
public class TrailGame {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String name;

	/** How many turns the game lasts, 30 to 50. */
	@Column(name = "turn_count", nullable = false)
	private int turns;

	/** Turns played so far; 0 before the first one. */
	@Column(name = "current_turn", nullable = false)
	private int turn;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(length = 200)
	private String prize;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Handicap handicap = Handicap.NONE;

	/** Every shop price is multiplied by this, from the players' usual sales. */
	@Column(name = "price_scale", nullable = false)
	private double priceScale = 1;

	@Column(name = "bonus_treatment", nullable = false)
	private int bonusTreatment;

	@Column(name = "bonus_color", nullable = false)
	private int bonusColor;

	@Column(name = "bonus_wax", nullable = false)
	private int bonusWax;

	/** The furthest landmark someone has reached; the first to each one is announced. */
	@Column(name = "furthest_landmark", nullable = false)
	private int furthestLandmark;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "last_checked_at")
	private LocalDateTime lastCheckedAt;

	/** Why coins couldn't be read at the last turn, shown to managers. */
	@Column(name = "check_problem", length = 500)
	private String checkProblem;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	public Long getId() { return id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public int getTurns() { return turns; }
	public void setTurns(int turns) { this.turns = turns; }

	public int getTurn() { return turn; }
	public void setTurn(int turn) { this.turn = turn; }

	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

	public String getPrize() { return prize; }
	public void setPrize(String prize) { this.prize = prize; }

	public Handicap getHandicap() { return handicap == null ? Handicap.NONE : handicap; }
	public void setHandicap(Handicap handicap) { this.handicap = handicap; }

	public double getPriceScale() { return priceScale; }
	public void setPriceScale(double priceScale) { this.priceScale = priceScale; }

	public int getBonusTreatment() { return bonusTreatment; }
	public void setBonusTreatment(int bonusTreatment) { this.bonusTreatment = bonusTreatment; }

	public int getBonusColor() { return bonusColor; }
	public void setBonusColor(int bonusColor) { this.bonusColor = bonusColor; }

	public int getBonusWax() { return bonusWax; }
	public void setBonusWax(int bonusWax) { this.bonusWax = bonusWax; }

	public int getFurthestLandmark() { return furthestLandmark; }
	public void setFurthestLandmark(int furthestLandmark) { this.furthestLandmark = furthestLandmark; }

	public Long getCreatedBy() { return createdBy; }
	public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
	public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

	public String getCheckProblem() { return checkProblem; }
	public void setCheckProblem(String checkProblem) { this.checkProblem = checkProblem; }

	public LocalDateTime getFinishedAt() { return finishedAt; }
	public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
