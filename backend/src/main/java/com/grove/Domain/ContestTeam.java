package com.grove.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A named team in a contest. People point at it by position, so renaming keeps everyone on it. */
@Entity
@Table(name = "contest_teams", indexes = @Index(name = "ix_contest_team_contest", columnList = "contest_id"))
public class ContestTeam {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "contest_id", nullable = false)
	private Long contestId;

	@Column(name = "position", nullable = false)
	private Integer position;

	@Column(nullable = false, length = 40)
	private String name;

	public Long getId() { return id; }

	public Long getContestId() { return contestId; }
	public void setContestId(Long contestId) { this.contestId = contestId; }

	public Integer getPosition() { return position; }
	public void setPosition(Integer position) { this.position = position; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
