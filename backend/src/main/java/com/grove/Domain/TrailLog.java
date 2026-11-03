package com.grove.Domain;

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
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * What happened on the trail. Lines with a party are seen by that party; lines
 * without one by everyone in the game. PURCHASE lines record who paid what.
 */
@Entity
@Table(name = "trail_log", indexes = @Index(name = "ix_trail_log_game", columnList = "game_id"))
public class TrailLog {

	public enum Kind { STORY, PURCHASE, THEFT, HELP, PARTY, LANDMARK }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false)
	private Long gameId;

	@Column(name = "party_id")
	private Long partyId;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "turn_number", nullable = false)
	private int turn;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Kind kind;

	@Column(nullable = false, length = 500)
	private String body;

	/** For purchases, what this person paid. */
	@Column
	private Integer coins;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Long getId() { return id; }

	public Long getGameId() { return gameId; }
	public void setGameId(Long gameId) { this.gameId = gameId; }

	public Long getPartyId() { return partyId; }
	public void setPartyId(Long partyId) { this.partyId = partyId; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public int getTurn() { return turn; }
	public void setTurn(int turn) { this.turn = turn; }

	public Kind getKind() { return kind; }
	public void setKind(Kind kind) { this.kind = kind; }

	public String getBody() { return body; }
	public void setBody(String body) { this.body = body; }

	public Integer getCoins() { return coins; }
	public void setCoins(Integer coins) { this.coins = coins; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
