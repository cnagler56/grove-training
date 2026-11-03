package com.grove.Service;

import java.util.List;

import com.grove.Domain.TrailParty.Pace;
import com.grove.Domain.TrailPlayer.Mount;

/**
 * Every tuning number for the Grove Trail in one place: the map, speeds, prices,
 * the rubber band that quietly helps whoever is behind, karma, and stealing.
 */
public final class TrailOdds {

	private TrailOdds() {}

	// The map.

	/** A stop on the route, where it is on a map, and whether it has a trading post. */
	public record Landmark(String name, int miles, double lon, double lat, boolean store) {}

	/** An 1800s overland route: no roads, just rivers, forts, and trading posts between them. */
	public static final List<Landmark> LANDMARKS = List.of(
		new Landmark("Maple Grove, Minnesota Territory", 0, -93.46, 45.07, true),
		new Landmark("The Crow River", 70, -94.35, 45.25, false),
		new Landmark("Otter Tail Lake", 150, -95.6, 46.3, false),
		new Landmark("Fort Abercrombie on the Red River", 235, -96.73, 46.45, true),
		new Landmark("The Sheyenne River", 330, -97.95, 46.95, false),
		new Landmark("The James River", 420, -98.75, 47.1, false),
		new Landmark("Fort Berthold on the Missouri", 560, -101.55, 47.6, true),
		new Landmark("Fort Union", 690, -104.04, 47.99, true),
		new Landmark("The Yellowstone River", 820, -106.2, 46.7, false),
		new Landmark("Pompeys Pillar", 960, -108.0, 45.99, false),
		new Landmark("Three Forks of the Missouri", 1130, -111.5, 45.9, false),
		new Landmark("The Continental Divide", 1200, -112.4, 46.65, false),
		new Landmark("Hell Gate", 1290, -114.0, 46.87, true),
		new Landmark("The Coeur d'Alene Mission", 1410, -116.36, 47.55, true),
		new Landmark("Spokane Falls", 1460, -117.43, 47.66, false),
		new Landmark("Fort Walla Walla", 1600, -118.9, 46.07, true),
		new Landmark("The Dalles", 1720, -121.18, 45.6, false),
		new Landmark("The Cascade Rapids", 1780, -121.9, 45.66, false),
		new Landmark("Fort Vancouver", 1810, -122.66, 45.62, true),
		new Landmark("The Pacific at Haystack Rock", 1870, -123.96, 45.89, false));

	public static final int TRAIL_MILES = 1870;

	/** The last landmark at or before these miles (0 is the salon). */
	public static int landmarkAt(int miles) {
		int at = 0;
		for (int i = 0; i < LANDMARKS.size(); i++) if (LANDMARKS.get(i).miles() <= miles) at = i;
		return at;
	}

	// Game length and the start.

	public static final int MIN_TURNS = 30;
	public static final int MAX_TURNS = 50;
	public static final int DEFAULT_TURNS = 40;
	public static final int STARTING_COINS = 200;
	public static final int STARTING_FOOD = 5;
	public static final int MAX_PARTY = 4;
	public static final int DEFAULT_BONUS_TREATMENT = 25;
	public static final int DEFAULT_BONUS_COLOR = 20;
	public static final int DEFAULT_BONUS_WAX = 15;

	// Speeds, in miles per turn for a 40 turn game; shorter games move faster, so each mode takes the same share of the game.

	public static final int WALK_SPEED = 28;
	public static final int DONKEY_SPEED = 40;
	public static final int HORSE_SPEED = 52;
	public static final int WAGON_SPEED = 64;

	public static int speed(Mount m, boolean shoeThrown) {
		return switch (m) {
			case WALK -> WALK_SPEED;
			case DONKEY -> DONKEY_SPEED;
			case HORSE -> shoeThrown ? WALK_SPEED : HORSE_SPEED;
		};
	}

	/** How much faster than a 40 turn game everything moves. */
	public static double lengthScale(int turns) {
		return (double) DEFAULT_TURNS / turns;
	}

	public static double paceSpeed(Pace pace) {
		return switch (pace) {
			case LEISURELY -> 0.8;
			case STEADY -> 1.0;
			case GRUELING -> 1.25;
		};
	}

	/** Snack packs each person eats a turn. */
	public static int paceFood(Pace pace) {
		return pace == Pace.GRUELING ? 2 : 1;
	}

	/** Odds points: slower is safer. */
	public static int paceLuck(Pace pace) {
		return switch (pace) {
			case LEISURELY -> 5;
			case STEADY -> 0;
			case GRUELING -> -5;
		};
	}

	/** Out of food: this much of the usual speed. */
	public static final double HANGRY_SPEED = 0.5;

	/** A wagon breaks down on its own this often each turn (doubled on Grueling), on top of risky scenario choices. */
	public static final double WAGON_BREAKDOWN = 0.02;

	// Prices, for a group that averages BASE_DAILY_SALES a day; scaled to the players' real average when a game is made.

	public static final double BASE_DAILY_SALES = 800;
	public static final double MIN_PRICE_SCALE = 0.5;
	public static final double MAX_PRICE_SCALE = 3;

	public enum Item {
		FOOD("Provisions", 12, "Flour, bacon, and coffee for one person for one turn."),
		DONKEY("Donkey", 800, "40 miles a turn. Stubborn. Loves carrots."),
		HORSE("Horse", 2000, "52 miles a turn. Throws shoes. Spooks easily."),
		WAGON("Covered wagon with oxen", 6000, "64 miles a turn for up to 4 people. Can break down."),
		REPAIR("Wheelwright", 1200, "Gets a broken-down wagon rolling again."),
		SPARE_WHEEL("Spare wagon wheel", 400, "Fixes a breakdown on the spot."),
		HORSESHOES("Horseshoes", 150, "Gets a horse that threw a shoe back to full speed."),
		CARROTS("Carrots", 60, "Convinces a donkey that has stopped."),
		PLAYLIST("A fiddle", 100, "Settles one argument about which way to go.");

		public final String label;
		public final int basePrice;
		public final String help;

		Item(String label, int basePrice, String help) {
			this.label = label;
			this.basePrice = basePrice;
			this.help = help;
		}
	}

	/** A price in this game, to the nearest 5 (snack packs to the nearest 1). */
	public static int price(Item item, double scale) {
		double raw = item.basePrice * scale;
		if (item == Item.FOOD) return Math.max(1, (int) Math.round(raw));
		return Math.max(5, (int) Math.round(raw / 5) * 5);
	}

	/** Leftover wagon when two wagon owners join up: sold back for this share of the price. */
	public static final double WAGON_RESALE = 0.5;

	// The rubber band: every scenario option has a base chance of going well, moved by where the party is.

	/** Odds points per share of the trail behind the average (1.0 = the whole trail). */
	public static final double CATCH_UP = 150;
	/** The most the odds move from an option's base, all bonuses together. */
	public static final int MAX_SHIFT = 20;
	public static final int MIN_CHANCE = 10;
	public static final int MAX_CHANCE = 90;
	/** A puppy is good for morale for the rest of the game. */
	public static final int PUPPY_LUCK = 10;

	/**
	 * The chance (0 to 100) an option turns out well. {@code behind} is how far the party trails the
	 * average as a share of the trail (negative when ahead); {@code bonus} is karma, pace, and luck.
	 */
	public static int chance(int base, double behind, int bonus) {
		int shift = (int) Math.round(CATCH_UP * behind) + bonus;
		shift = Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, shift));
		return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, base + shift));
	}

	// Karma.

	public static final int THEFT_KARMA = -5;
	public static final int HELP_KARMA = 5;
	public static final int MAX_KARMA = 15;
	public static final int KARMA_TURNS = 4;
	public static final int SAMARITAN_TURNS = 6;

	// Parties.

	/** How long someone has to answer a request to travel together. */
	public static final int INVITE_TURNS = 4;
	/** After leaving a party, how long before joining another. Voted out: double. */
	public static final int SPACE_TURNS = 4;

	// Skimming from your own party.

	public static final double SKIM_OFFER = 0.06;
	/** No offers before this turn, more often than this, or this soon after joining. */
	public static final int SKIM_FIRST_TURN = 4;
	public static final int SKIM_EVERY = 5;
	public static final int SKIM_AFTER_JOINING = 2;
	public static final double SKIM_COIN_SHARE = 0.15;
	public static final int SKIM_COIN_MAX = 300;
	public static final int SKIM_FOOD_MAX = 3;
	public static final int SKIM_CAUGHT = 40;
	/** Victims can get even, or vote the thief out, for this many turns. */
	public static final int AFTERMATH_TURNS = 3;
	public static final int GET_EVEN_WORKS = 60;

	// Trading posts: at the forts, and now and then a traveling trader.

	/** Chance a traveling trader turns up on a turn that doesn't pass a trading post. */
	public static final double PEDDLER = 0.15;

	// Meeting someone on the trail.

	/** Chance of running into another party on the same stretch of trail (since the same landmark). */
	public static final double MEET_CHANCE = 0.4;
	/** Chance a meeting comes with the option to travel together, when the parties could. */
	public static final double JOIN_OFFER = 0.35;
	/** The same two can't meet again for this many turns. */
	public static final int MEET_AGAIN_TURNS = 6;
	public static final int HELP_FOOD_MAX = 5;
	public static final int HELP_COIN_MAX = 300;
	/** Fixing someone else's wagon: you know a guy. */
	public static final double HELP_REPAIR_SHARE = 0.75;
	public static final double STEAL_COIN_SHARE = 0.20;
	public static final int STEAL_COIN_MAX = 250;
	public static final int STEAL_FOOD_MAX = 3;
	public static final int STEAL_MIN = 25;
	public static final int STEAL_MAX = 70;
	/** Everyone is looking at the broken-down wagon. */
	public static final int STEAL_FROM_BROKEN = -10;

	/**
	 * The chance (0 to 100) a theft works: easier from someone better off, harder from someone
	 * worse off, so leaders can't snowball. Worth is coins plus food and spare parts.
	 */
	public static int stealChance(int thiefWorth, int targetWorth, boolean targetBroken) {
		int total = Math.max(1, thiefWorth + targetWorth);
		int chance = 50 + (int) Math.round(40.0 * (targetWorth - thiefWorth) / total);
		chance = Math.max(STEAL_MIN, Math.min(STEAL_MAX, chance));
		if (targetBroken) chance += STEAL_FROM_BROKEN;
		return Math.max(5, Math.min(95, chance));
	}
}
