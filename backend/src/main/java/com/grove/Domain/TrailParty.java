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
 * People traveling together on the trail; someone going alone is a party of one.
 * Food, the wagon, and spare parts belong to the party; coins and animals to each person.
 */
@Entity
@Table(name = "trail_parties", indexes = @Index(name = "ix_trail_party_game", columnList = "game_id"))
public class TrailParty {

	public enum Pace { LEISURELY, STEADY, GRUELING }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false)
	private Long gameId;

	@Column(nullable = false)
	private int miles;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Pace pace = Pace.STEADY;

	/** Snack packs. */
	@Column(nullable = false)
	private int food;

	@Column(nullable = false)
	private boolean wagon;

	@Column(name = "wagon_broken", nullable = false)
	private boolean wagonBroken;

	@Column(name = "spare_wheels", nullable = false)
	private int spareWheels;

	@Column(nullable = false)
	private int horseshoes;

	@Column(nullable = false)
	private int carrots;

	@Column(nullable = false)
	private int playlists;

	@Column(nullable = false)
	private boolean puppy;

	/** Ran out of food: half speed until they have some again. */
	@Column(nullable = false)
	private boolean hangry;

	/** Points added to the next scenario's odds, then cleared. */
	@Column(nullable = false)
	private int luck;

	@Column(name = "arrived_turn")
	private Integer arrivedTurn;

	/** Turns this party has played; it can play another while this is below the game's unlocked turns. */
	@Column(name = "turns_played", nullable = false)
	private int turnsPlayed;

	/** The next turn's scenario. Null while a meeting takes its place, or at the coast. */
	@Column(name = "scenario_key", length = 40)
	private String scenarioKey;

	/** Another party this one ran into; the next turn is about what to do about them. */
	@Column(name = "encounter_party_id")
	private Long encounterPartyId;

	/** Whether that meeting comes with the option to travel together. */
	@Column(name = "encounter_join", nullable = false)
	private boolean encounterJoin;

	/** The general store here (a town's name, or a peddler), open until the party moves on. Null when there's none. */
	@Column(name = "store_name", length = 80)
	private String storeName;

	/** Scenarios already dealt, comma separated, so nobody gets a repeat until they've seen them all. */
	@Column(length = 2000)
	private String seen;

	public Long getId() { return id; }

	public Long getGameId() { return gameId; }
	public void setGameId(Long gameId) { this.gameId = gameId; }

	public int getMiles() { return miles; }
	public void setMiles(int miles) { this.miles = miles; }

	public Pace getPace() { return pace == null ? Pace.STEADY : pace; }
	public void setPace(Pace pace) { this.pace = pace; }

	public int getFood() { return food; }
	public void setFood(int food) { this.food = food; }

	public boolean isWagon() { return wagon; }
	public void setWagon(boolean wagon) { this.wagon = wagon; }

	public boolean isWagonBroken() { return wagonBroken; }
	public void setWagonBroken(boolean wagonBroken) { this.wagonBroken = wagonBroken; }

	public int getSpareWheels() { return spareWheels; }
	public void setSpareWheels(int spareWheels) { this.spareWheels = spareWheels; }

	public int getHorseshoes() { return horseshoes; }
	public void setHorseshoes(int horseshoes) { this.horseshoes = horseshoes; }

	public int getCarrots() { return carrots; }
	public void setCarrots(int carrots) { this.carrots = carrots; }

	public int getPlaylists() { return playlists; }
	public void setPlaylists(int playlists) { this.playlists = playlists; }

	public boolean isPuppy() { return puppy; }
	public void setPuppy(boolean puppy) { this.puppy = puppy; }

	public boolean isHangry() { return hangry; }
	public void setHangry(boolean hangry) { this.hangry = hangry; }

	public int getLuck() { return luck; }
	public void setLuck(int luck) { this.luck = luck; }

	public Integer getArrivedTurn() { return arrivedTurn; }
	public void setArrivedTurn(Integer arrivedTurn) { this.arrivedTurn = arrivedTurn; }

	public int getTurnsPlayed() { return turnsPlayed; }
	public void setTurnsPlayed(int turnsPlayed) { this.turnsPlayed = turnsPlayed; }

	public String getScenarioKey() { return scenarioKey; }
	public void setScenarioKey(String scenarioKey) { this.scenarioKey = scenarioKey; }

	public Long getEncounterPartyId() { return encounterPartyId; }
	public void setEncounterPartyId(Long encounterPartyId) { this.encounterPartyId = encounterPartyId; }

	public boolean isEncounterJoin() { return encounterJoin; }
	public void setEncounterJoin(boolean encounterJoin) { this.encounterJoin = encounterJoin; }

	public String getStoreName() { return storeName; }
	public void setStoreName(String storeName) { this.storeName = storeName; }

	public String getSeen() { return seen; }
	public void setSeen(String seen) { this.seen = seen; }
}
