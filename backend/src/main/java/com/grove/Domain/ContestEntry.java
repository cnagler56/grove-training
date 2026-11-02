package com.grove.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One person in a contest, with their count and place as of the last check. */
@Entity
@Table(name = "contest_entries", indexes = @Index(name = "ix_contest_entry_contest", columnList = "contest_id"))
public class ContestEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "contest_id", nullable = false)
	private Long contestId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** Their team's position (0, 1, ...) in the contest's teams; null when there are no teams. */
	@Column(name = "team")
	private Integer team;

	/** Null until the first check. */
	@Column(name = "entry_count")
	private Integer count;

	/** With a handicap, points per service (100 to 200), set at the first check and kept for the whole contest. */
	@Column(name = "worth")
	private Integer worth;

	/** Count times worth with a handicap, otherwise the count. What places are decided by. */
	@Column(name = "points")
	private Integer points;

	/** 1 is first; people who are tied share a place. Null until the first check. */
	@Column(name = "entry_rank")
	private Integer rank;

	public Long getId() { return id; }

	public Long getContestId() { return contestId; }
	public void setContestId(Long contestId) { this.contestId = contestId; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public Integer getTeam() { return team; }
	public void setTeam(Integer team) { this.team = team; }

	public Integer getCount() { return count; }
	public void setCount(Integer count) { this.count = count; }

	public Integer getWorth() { return worth; }
	public void setWorth(Integer worth) { this.worth = worth; }

	/** Entries checked before points existed have only a count. */
	public Integer getPoints() { return points != null ? points : count; }
	public void setPoints(Integer points) { this.points = points; }

	public Integer getRank() { return rank; }
	public void setRank(Integer rank) { this.rank = rank; }
}
