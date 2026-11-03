package com.grove.Domain;

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
 * A question for one person or one party that isn't the turn's scenario: a request
 * to travel together, a chance to skim, getting even, or voting a thief out. MEET
 * rows only remember who ran into whom.
 */
@Entity
@Table(name = "trail_choices", indexes = @Index(name = "ix_trail_choice_game", columnList = "game_id"))
public class TrailChoice {

	/**
	 * INVITE: party {@code partyId} is asked by {@code otherUserId} (of {@code otherPartyId}) to travel together.
	 * SKIM: {@code userId} can take from their own party {@code partyId}.
	 * MEET: a record that {@code partyId} ran into {@code otherPartyId}, so they don't meet again too soon.
	 * GET_EVEN: {@code userId} can take back {@code amount} coins' worth from thief {@code otherUserId}.
	 * VOTE_OUT: {@code userId} can vote thief {@code otherUserId} out of {@code partyId}.
	 */
	public enum Kind { INVITE, SKIM, MEET, GET_EVEN, VOTE_OUT }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false)
	private Long gameId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Kind kind;

	@Column(name = "party_id")
	private Long partyId;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "other_party_id")
	private Long otherPartyId;

	@Column(name = "other_user_id")
	private Long otherUserId;

	@Column(nullable = false)
	private int amount;

	@Column(name = "offered_turn", nullable = false)
	private int offeredTurn;

	/** Still open through this turn; unanswered ones then get their default. */
	@Column(name = "expires_turn", nullable = false)
	private int expiresTurn;

	@Column(length = 20)
	private String answer;

	@Column(name = "answered_by")
	private Long answeredBy;

	@Column(nullable = false)
	private boolean resolved;

	public Long getId() { return id; }

	public Long getGameId() { return gameId; }
	public void setGameId(Long gameId) { this.gameId = gameId; }

	public Kind getKind() { return kind; }
	public void setKind(Kind kind) { this.kind = kind; }

	public Long getPartyId() { return partyId; }
	public void setPartyId(Long partyId) { this.partyId = partyId; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public Long getOtherPartyId() { return otherPartyId; }
	public void setOtherPartyId(Long otherPartyId) { this.otherPartyId = otherPartyId; }

	public Long getOtherUserId() { return otherUserId; }
	public void setOtherUserId(Long otherUserId) { this.otherUserId = otherUserId; }

	public int getAmount() { return amount; }
	public void setAmount(int amount) { this.amount = amount; }

	public int getOfferedTurn() { return offeredTurn; }
	public void setOfferedTurn(int offeredTurn) { this.offeredTurn = offeredTurn; }

	public int getExpiresTurn() { return expiresTurn; }
	public void setExpiresTurn(int expiresTurn) { this.expiresTurn = expiresTurn; }

	public String getAnswer() { return answer; }
	public void setAnswer(String answer) { this.answer = answer; }

	public Long getAnsweredBy() { return answeredBy; }
	public void setAnsweredBy(Long answeredBy) { this.answeredBy = answeredBy; }

	public boolean isResolved() { return resolved; }
	public void setResolved(boolean resolved) { this.resolved = resolved; }
}
