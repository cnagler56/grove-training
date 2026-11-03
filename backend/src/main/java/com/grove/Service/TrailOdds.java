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

	public record Landmark(String name, int miles) {}

	public static final List<Landmark> LANDMARKS = List.of(
		new Landmark("Maple Grove, MN", 0),
		new Landmark("St. Cloud, MN", 65),
		new Landmark("Big Ole the Viking, Alexandria", 135),
		new Landmark("Fargo, ND", 235),
		new Landmark("The World's Largest Buffalo, Jamestown", 330),
		new Landmark("Salem Sue, the World's Largest Holstein", 440),
		new Landmark("The Enchanted Highway", 490),
		new Landmark("Medora, ND", 560),
		new Landmark("Miles City, MT", 700),
		new Landmark("Pompeys Pillar", 815),
		new Landmark("Billings, MT", 845),
		new Landmark("Bozeman, MT", 990),
		new Landmark("The Continental Divide", 1060),
		new Landmark("Missoula, MT", 1180),
		new Landmark("Lookout Pass, ID", 1280),
		new Landmark("Coeur d'Alene, ID", 1350),
		new Landmark("Spokane, WA", 1385),
		new Landmark("The Columbia River Gorge", 1640),
		new Landmark("Multnomah Falls", 1760),
		new Landmark("Portland, OR", 1790),
		new Landmark("Haystack Rock, Cannon Beach", 1870));

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
	public static final int STARTING_FOOD = 3;
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
		FOOD("Snack pack", 12, "Feeds one person for one turn (two on Grueling)."),
		DONKEY("Donkey", 800, "40 miles a turn. Stubborn. Loves carrots."),
		HORSE("Horse", 2000, "52 miles a turn. Throws shoes. Afraid of plastic bags."),
		WAGON("Wagon with oxen", 6000, "64 miles a turn for up to 4 people. Can break down."),
		REPAIR("Wagon repair", 1200, "Gets a broken-down wagon moving again."),
		SPARE_WHEEL("Spare wheel", 400, "Fixes a breakdown on the spot, for free."),
		HORSESHOES("Horseshoes", 150, "Gets a horse that threw a shoe back to full speed."),
		CARROTS("Carrots", 60, "Convinces a donkey that is having a moment."),
		PLAYLIST("A Really Good Playlist", 100, "Settles one aux cord war.");

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

	/** You can invite people this close to you. */
	public static final int INVITE_MILES = 60;
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

	// Meeting someone on the trail.

	public static final int MEET_MILES = 10;
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
	/** The target held the door: they chose to help while being robbed. */
	public static final int STEAL_FROM_HELPER = 15;
	/** Everyone is looking at the broken-down wagon. */
	public static final int STEAL_FROM_BROKEN = -10;

	/**
	 * The chance (0 to 100) a theft works: easier from someone better off, harder from someone
	 * worse off, so leaders can't snowball. Worth is coins plus food and spare parts.
	 */
	public static int stealChance(int thiefWorth, int targetWorth, boolean targetHelped, boolean targetBroken) {
		int total = Math.max(1, thiefWorth + targetWorth);
		int chance = 50 + (int) Math.round(40.0 * (targetWorth - thiefWorth) / total);
		chance = Math.max(STEAL_MIN, Math.min(STEAL_MAX, chance));
		if (targetHelped) chance += STEAL_FROM_HELPER;
		if (targetBroken) chance += STEAL_FROM_BROKEN;
		return Math.max(5, Math.min(95, chance));
	}
}
