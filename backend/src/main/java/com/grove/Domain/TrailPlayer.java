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

/** One person on the trail: their coins, their animal, and their reputation. */
@Entity
@Table(name = "trail_players", indexes = @Index(name = "ix_trail_player_game", columnList = "game_id"))
public class TrailPlayer {

	public enum Mount { WALK, DONKEY, HORSE }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false)
	private Long gameId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "party_id", nullable = false)
	private Long partyId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private Mount mount = Mount.WALK;

	/** A horse that threw a shoe goes at walking speed until the party has horseshoes. */
	@Column(name = "shoe_thrown", nullable = false)
	private boolean shoeThrown;

	/** Coins to spend now. */
	@Column(nullable = false)
	private int coins;

	/** Coins credited from Salon Ultimate so far, so each turn adds only what's new. */
	@Column(nullable = false)
	private int earned;

	/** With a handicap, what $100 of sales is worth (100 to 200), set when the game is made. */
	@Column(nullable = false)
	private int worth = 100;

	/** Odds points from stealing (negative) or helping (positive), until the turn after {@code karmaUntil}. */
	@Column(nullable = false)
	private int karma;

	@Column(name = "karma_until", nullable = false)
	private int karmaUntil;

	@Column(name = "sticky_fingers", nullable = false)
	private boolean stickyFingers;

	@Column(name = "samaritan_until", nullable = false)
	private int samaritanUntil;

	/** Can't join a party before this turn ("needs some space"). */
	@Column(name = "no_join_until", nullable = false)
	private int noJoinUntil;

	@Column(name = "joined_turn", nullable = false)
	private int joinedTurn;

	@Column(name = "last_skim_turn", nullable = false)
	private int lastSkimTurn;

	public Long getId() { return id; }

	public Long getGameId() { return gameId; }
	public void setGameId(Long gameId) { this.gameId = gameId; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public Long getPartyId() { return partyId; }
	public void setPartyId(Long partyId) { this.partyId = partyId; }

	public Mount getMount() { return mount == null ? Mount.WALK : mount; }
	public void setMount(Mount mount) { this.mount = mount; }

	public boolean isShoeThrown() { return shoeThrown; }
	public void setShoeThrown(boolean shoeThrown) { this.shoeThrown = shoeThrown; }

	public int getCoins() { return coins; }
	public void setCoins(int coins) { this.coins = coins; }

	public int getEarned() { return earned; }
	public void setEarned(int earned) { this.earned = earned; }

	public int getWorth() { return worth; }
	public void setWorth(int worth) { this.worth = worth; }

	public int getKarma() { return karma; }
	public void setKarma(int karma) { this.karma = karma; }

	public int getKarmaUntil() { return karmaUntil; }
	public void setKarmaUntil(int karmaUntil) { this.karmaUntil = karmaUntil; }

	public boolean isStickyFingers() { return stickyFingers; }
	public void setStickyFingers(boolean stickyFingers) { this.stickyFingers = stickyFingers; }

	public int getSamaritanUntil() { return samaritanUntil; }
	public void setSamaritanUntil(int samaritanUntil) { this.samaritanUntil = samaritanUntil; }

	public int getNoJoinUntil() { return noJoinUntil; }
	public void setNoJoinUntil(int noJoinUntil) { this.noJoinUntil = noJoinUntil; }

	public int getJoinedTurn() { return joinedTurn; }
	public void setJoinedTurn(int joinedTurn) { this.joinedTurn = joinedTurn; }

	public int getLastSkimTurn() { return lastSkimTurn; }
	public void setLastSkimTurn(int lastSkimTurn) { this.lastSkimTurn = lastSkimTurn; }
}
