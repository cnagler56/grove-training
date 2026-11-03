package com.grove.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Grove Trail's scenarios. One is dealt to every party each turn; each option has a
 * base chance (out of 100) of going well, which {@link TrailOdds#chance} moves for
 * whoever is behind or ahead. 100 means it always goes the good way.
 */
public final class TrailScenarios {

	private TrailScenarios() {}

	/** Who can be dealt a scenario: anyone, only parties of 2 or more, or one way of getting around. */
	public enum Who { ANY, PARTY, WALK, DONKEY, HORSE, WAGON }

	/** What an option needs before it can be picked. */
	public enum Needs { NOTHING, WAGON, CARROTS, PLAYLIST }

	/**
	 * What happens to the party. {@code move} multiplies this turn's miles (0 = lose a turn);
	 * {@code coins} is for the whole party, split evenly; {@code luck} is odds points for the next scenario.
	 */
	public static final class Fx {
		public final String text;
		public double move = 1;
		public int miles, food, coins, luck, wheels, shoes, carrots;
		public boolean breakWagon, throwShoe, puppy, cureHangry, horseForDonkey;

		Fx(String text) { this.text = text; }

		Fx move(double m) { move = m; return this; }
		Fx miles(int n) { miles = n; return this; }
		Fx food(int n) { food = n; return this; }
		Fx coins(int n) { coins = n; return this; }
		Fx luck(int n) { luck = n; return this; }
		Fx wheels(int n) { wheels = n; return this; }
		Fx shoes(int n) { shoes = n; return this; }
		Fx carrots(int n) { carrots = n; return this; }
		Fx breakWagon() { breakWagon = true; return this; }
		Fx throwShoe() { throwShoe = true; return this; }
		Fx puppy() { puppy = true; return this; }
		Fx cureHangry() { cureHangry = true; return this; }
		Fx horseForDonkey() { horseForDonkey = true; return this; }
	}

	/** {@code cost} is paid up front (by each person when {@code costEach}); without the coins, the bad outcome happens. */
	public static final class Option {
		public final String label;
		public final int base;
		public final Fx good, bad;
		public boolean isDefault;
		public Needs needs = Needs.NOTHING;
		public int cost;
		public boolean costEach;

		Option(String label, int base, Fx good, Fx bad) {
			this.label = label;
			this.base = base;
			this.good = good;
			this.bad = bad;
		}

		Option def() { isDefault = true; return this; }
		Option needs(Needs n) { needs = n; return this; }
		Option cost(int n) { cost = n; return this; }
		Option costEach(int n) { cost = n; costEach = true; return this; }

		public boolean sure() { return base >= 100; }
	}

	public record Scenario(String key, Who who, String text, List<Option> options) {
		/** The option used when nobody answers. */
		public int defaultOption() {
			for (int i = 0; i < options.size(); i++) if (options.get(i).isDefault) return i;
			return 0;
		}
	}

	private static Fx fx(String text) { return new Fx(text); }

	private static Option o(String label, int base, Fx good, Fx bad) { return new Option(label, base, good, bad); }

	/** An option that always goes one way. */
	private static Option sure(String label, Fx result) { return new Option(label, 100, result, result); }

	private static Scenario s(String key, Who who, String text, Option... options) {
		return new Scenario(key, who, text, List.of(options));
	}

	private static final Fx NOTHING = fx("Nothing happens. The trail goes on.");

	public static final List<Scenario> ALL = List.of(
		// On the road.
		s("river", Who.ANY, "A crystal clear river. You are so thirsty.",
			o("Drink from it", 60, fx("Energized and refreshed. +30 miles.").miles(30), fx("Polluted. You're sick and lose a turn.").move(0)),
			o("Drink the emergency LaCroix", 85, fx("Fine. +5 miles.").miles(5), fx("It's pamplemousse. -10 miles of complaining.").miles(-10)).def(),
			o("Boil it first", 95, fx("Safe, but you lose a quarter turn.").move(0.75), fx("You forgot the pot. Lose half a turn.").move(0.5))),
		s("sushi", Who.ANY, "A gas station sushi display, glowing softly.",
			o("Eat it", 35, fx("Surprisingly great. +3 snack packs.").food(3), fx("You have died of dysentery. Just kidding. Lose a turn.").move(0)),
			o("Buy a Glazer instead, 10 coins", 90, fx("A Glazer. +1 snack pack.").food(1), fx("Sold out. You stare at the empty case.")).cost(10).def()),
		s("goose", Who.ANY, "A goose blocks the trail and will not be negotiated with.",
			o("Negotiate anyway", 40, fx("It respects you. +20 miles.").miles(20), fx("It does not respect you. -30 miles.").miles(-30)),
			o("Go around", 85, fx("-10 miles.").miles(-10), fx("Around is very far. -20 miles.").miles(-20)).def(),
			o("Offer it a snack pack", 70, fx("It takes the snack and leaves. -1 snack pack.").food(-1),
				fx("It eats the snack and stays. -1 snack pack, -20 miles.").food(-1).miles(-20))),
		s("frontage", Who.ANY, "A frontage road that Google Maps swears exists.",
			o("Take it", 55, fx("A real shortcut. +40 miles.").miles(40), fx("It does not exist. -30 miles.").miles(-30)),
			o("Stay on the interstate", 90, NOTHING, fx("Construction. -10 miles.").miles(-10)).def()),
		s("cones", Who.ANY, "Road construction, as in every season.",
			o("Follow the detour signs", 75, fx("-10 miles.").miles(-10), fx("The detour has a detour. -30 miles.").miles(-30)).def(),
			o("Drive through the cones", 40, fx("Nobody noticed. +20 miles.").miles(20), fx("Fined 150 coins.").coins(-150))),
		s("flatiron", Who.ANY, "Did you leave the flat iron on?",
			o("Go back and check", 95, fx("It was off. -30 miles.").miles(-30), fx("It was off. You checked twice. -50 miles.").miles(-50)),
			o("Trust yourself", 60, fx("Peace of mind. +10 miles.").miles(10), fx("You think about it all turn. -20 miles.").miles(-20)).def()),
		s("canada", Who.ANY, "You may have taken a wrong turn.",
			o("Keep going, it'll work out", 40, fx("A scenic shortcut. +30 miles.").miles(30), fx("You're in Canada now. They were very nice about it. -40 miles.").miles(-40)),
			o("Turn around now", 90, fx("-10 miles.").miles(-10), fx("-20 miles.").miles(-20)).def()),
		s("phone", Who.ANY, "You drop your phone near the Missouri River.",
			o("Fish it out", 50, fx("Saved, and you find 50 coins in the mud.").coins(50), fx("It's gone. Navigating by vibes. -25 miles.").miles(-25)),
			o("Let it go", 90, fx("-15 miles.").miles(-15), fx("-25 miles.").miles(-25)).def()),
		s("tailwind", Who.ANY, "A tailwind across North Dakota, with literally nothing to stop it.",
			o("Ride it, full send", 65, fx("+50 miles.").miles(50), fx("You overshoot a gas stop. -1 snack pack.").food(-1)),
			o("Keep it steady", 90, fx("+20 miles.").miles(20), fx("+5 miles.").miles(5)).def()),
		s("kwiktrip", Who.ANY, "A Kwik Trip appears like a mirage.",
			o("Stock up, 30 coins", 80, fx("+4 snack packs.").food(4), fx("The Glazer case is empty. +1 snack pack.").food(1)).cost(30),
			o("Just the bathroom", 95, NOTHING, fx("The line. -10 miles.").miles(-10)).def()),
		s("hotdish", Who.ANY, "A church basement hot dish potluck.",
			o("Crash it", 65, fx("+5 snack packs.").food(5), fx("You're asked to lead the prayer. Lose half a turn.").move(0.5)),
			o("Bring bars (1 snack pack)", 85, fx("+6 snack packs, minus the bars.").food(5), fx("Your bars are judged. +1 snack pack, minus the bars.").food(1)),
			sure("Keep driving", NOTHING).def()),
		s("raffle", Who.ANY, "A meat raffle at a VFW.",
			o("Buy tickets, 40 coins", 40, fx("You win. +6 snack packs.").food(6), fx("You win nothing. The vibes were good.")).cost(40),
			sure("Just watch", fx("The vibes. +5 miles.").miles(5)).def()),
		s("snow", Who.ANY, "Snow in May.",
			o("Push through", 45, fx("+10 miles.").miles(10), fx("Stuck in a ditch. Lose a turn.").move(0)),
			o("Wait it out at a Perkins", 85, fx("Lose half a turn. +1 snack pack.").move(0.5).food(1), fx("Lose a turn.").move(0)).def()),
		s("mosquitoes", Who.ANY, "Mosquitoes, the state bird of Minnesota.",
			o("Run", 60, fx("+20 miles.").miles(20), fx("They carry off 2 snack packs.").food(-2)),
			o("Bug spray, 20 coins", 90, NOTHING, fx("The off-brand kind. -1 snack pack.").food(-1)).cost(20).def()),
		s("raccoons", Who.ANY, "Raccoons are circling the cooler.",
			o("Guard it all night", 70, fx("Saved, but you're tired. -10 miles.").miles(-10), fx("They win anyway. -3 snack packs.").food(-3)),
			o("Bungee it shut", 60, fx("Saved."), fx("They know bungees. -3 snack packs.").food(-3)).def()),
		s("buffalo", Who.ANY, "A buffalo herd is crossing the road.",
			o("Wait", 90, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def(),
			o("Inch through", 50, fx("+10 miles.").miles(10), fx("One sits on your bags. -2 snack packs, -20 miles.").food(-2).miles(-20))),
		s("hail", Who.ANY, "Hail warning in Montana.",
			o("Shelter in a Culver's", 90, fx("Lose half a turn. +2 snack packs.").move(0.5).food(2), fx("Lose a turn. +2 snack packs.").move(0).food(2)).def(),
			o("Push on", 45, fx("+30 miles.").miles(30), fx("Hail. Wagons break down; everyone else loses a turn.").move(0).breakWagon())),
		s("fog", Who.ANY, "A mysterious fog rolls in.",
			o("Push through", 50, fx("+40 miles.").miles(40), fx("Lost. -40 miles.").miles(-40)),
			o("Make camp", 90, fx("Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def()),
		s("springs", Who.ANY, "Montana hot springs.",
			o("Soak", 80, fx("Hangry cured. Next scenario more likely to go well.").cureHangry().luck(15), fx("You fall asleep. Lose a turn.").move(0)),
			sure("Skip it", NOTHING).def()),
		s("huckleberries", Who.ANY, "Wild huckleberries in Idaho.",
			o("Pick a bunch", 70, fx("+4 snack packs.").food(4), fx("Those were not huckleberries. Lose half a turn.").move(0.5)),
			o("Buy the jam, 30 coins", 95, fx("+2 snack packs.").food(2), fx("It's mostly sugar. +1 snack pack.").food(1)).cost(30).def()),
		s("nightdrive", Who.ANY, "Drive through the night?",
			o("Yes", 50, fx("+50 miles.").miles(50), fx("You nod off at a rest stop until noon. Lose half a turn, -1 snack pack.").move(0.5).food(-1)),
			sure("No", NOTHING).def()),
		s("scenic", Who.ANY, "Scenic route or interstate?",
			o("Scenic", 75, fx("-20 miles, but the next scenario is more likely to go well.").miles(-20).luck(15),
				fx("You get lost in the moment. -40 miles.").miles(-40)),
			o("Interstate", 90, NOTHING, fx("-10 miles.").miles(-10)).def()),
		s("ghosttown", Who.ANY, "A ghost town.",
			o("Explore", 50, fx("You find a spare wheel.").wheels(1), fx("A \"ghost\" raccoon takes 2 snack packs.").food(-2)),
			sure("Skip it", NOTHING).def()),
		s("elk", Who.ANY, "An elk is standing in the road.",
			o("Wait it out", 90, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def(),
			o("Honk", 45, fx("It leaves. +10 miles.").miles(10), fx("It charges. Wagons break down; everyone else loses a turn.").move(0).breakWagon())),

		// Salon life on the trail.
		s("freecut", Who.ANY, "A stranger at a rest stop asks for a free haircut.",
			o("Do it", 50, fx("He's a rich rancher. +200 coins.").coins(200), fx("He's just a guy. Lose half a turn.").move(0.5)),
			o("Recommend the salon", 85, fx("He books. +30 coins.").coins(30), fx("He leaves the salon a one star review.")).def()),
		s("bangs", Who.ANY, "A client texts \"small emergency.\"",
			o("FaceTime them", 60, fx("Fixed. They tip 100 coins.").coins(100), fx("Self-cut bangs. Lose half a turn.").move(0.5)),
			o("Reply \"be strong\"", 80, NOTHING, fx("They post about it. -20 miles of guilt.").miles(-20)).def()),
		s("wedding", Who.ANY, "A wedding party in Bozeman needs six updos.",
			o("Stay and do them", 65, fx("Lose a turn. +300 coins.").move(0).coins(300), fx("Lose a turn. The bride cries. +100 coins.").move(0).coins(100)),
			sure("Keep going", NOTHING).def()),
		s("roots", Who.ANY, "A rancher's wife needs a root touch-up.",
			o("Do it in the barn", 70, fx("+150 coins.").coins(150), fx("The lighting. +50 coins, and lose half a turn.").coins(50).move(0.5)),
			o("Give her your card", 90, fx("+20 coins.").coins(20), NOTHING).def()),
		s("tonic", Who.ANY, "A snake oil salesman offers Miracle Bond Repair Tonic.",
			o("Buy it, 150 coins", 40, fx("It works on oxen too, somehow. Double miles.").move(2), fx("It's just conditioner.")).cost(150),
			sure("Pass", NOTHING).def()),
		s("blowout", Who.ANY, "A rival salon's influencer challenges you to a blowout-off.",
			o("Accept", 50, fx("You win. +200 coins.").coins(200), fx("You lose. -100 coins and your dignity.").coins(-100)),
			o("Walk away", 90, NOTHING, fx("They post you walking away. -10 miles.").miles(-10)).def()),
		s("hitchhiker", Who.ANY, "A hitchhiker says he's a famous colorist.",
			o("Give him a ride", 50, fx("You trade formulas. +100 coins.").coins(100), fx("He steals 3 snack packs.").food(-3)),
			sure("No thanks", NOTHING).def()),
		s("mustache", Who.ANY, "The sheriff wants his mustache trimmed.",
			o("Do it", 70, fx("+50 coins and a police escort. +20 miles.").coins(50).miles(20), fx("You take a little too much. -50 coins.").coins(-50)),
			o("Decline", 60, fx("He nods."), fx("He's offended. -20 miles.").miles(-20)).def()),
		s("wifi", Who.ANY, "Free Wi-Fi at a rest stop.",
			o("Rebook clients", 75, fx("+80 coins.").coins(80), fx("The Wi-Fi is fake. Lose a quarter turn.").move(0.75)),
			o("Doom scroll", 90, fx("+5 miles of serenity.").miles(5), fx("-10 miles.").miles(-10)).def()),
		s("review", Who.ANY, "A five star review comes in, naming you.",
			o("Screenshot it for the group chat", 90, fx("+20 miles of morale.").miles(20), fx("Nobody reacts. +5 miles.").miles(5)).def(),
			o("Post it publicly", 60, fx("+50 miles of pure morale.").miles(50), fx("A stranger replies \"ok\"."))),
		s("lutefisk", Who.ANY, "The lutefisk challenge at a Sons of Norway hall.",
			o("Eat it", 45, fx("+4 snack packs, and respect.").food(4), fx("Lose a turn.").move(0)),
			o("Politely decline", 90, fx("Minnesota nice. Nothing happens."), fx("They ask again. Lose a quarter turn.").move(0.75)).def()),
		s("slots", Who.ANY, "Slot machines at a Montana gas station.",
			o("Bet 100 coins", 45, fx("+100 coins.").coins(100), fx("-100 coins.").coins(-100)),
			o("Bet 10 coins", 45, fx("+10 coins.").coins(10), fx("-10 coins.").coins(-10)),
			sure("Keep walking", NOTHING).def()),
		s("puppy", Who.ANY, "A lost puppy follows you.",
			o("Adopt it", 80, fx("Better luck for the rest of the game. It eats 1 snack pack a turn.").puppy(),
				fx("It runs away with a snack pack.").food(-1)),
			o("Drop it at a shelter", 95, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def()),

		// Only for one way of getting around.
		s("blisters", Who.WALK, "Blisters.",
			o("Walk it off", 50, NOTHING, fx("-40% miles.").move(0.6)),
			o("Moleskin, 20 coins", 90, NOTHING, fx("You're out of moleskin. -20 miles.").miles(-20)).cost(20).def()),
		s("trucker", Who.WALK, "A trucker at a Love's offers a ride.",
			o("Hop in", 70, fx("+60 miles.").miles(60), fx("He's only going to the next exit. +5 miles.").miles(5)),
			sure("Walk", NOTHING).def()),
		s("stubborn", Who.DONKEY, "Your donkey is having a moment.",
			o("Carrots", 95, fx("It moves. -1 carrots.").carrots(-1), fx("It eats the carrots and stays. Lose a turn.").carrots(-1).move(0)).needs(Needs.CARROTS),
			o("Reason with it", 35, fx("It moves."), fx("Lose a turn.").move(0)).def()),
		s("tiktok", Who.DONKEY, "Your donkey goes viral on TikTok.",
			o("Lean in", 55, fx("Fans tip 150 coins.").coins(150), fx("Fans swarm. Lose a turn.").move(0)),
			o("Hide", 85, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def()),
		s("horsegirl", Who.DONKEY, "A horse girl at a ranch offers to trade you a horse for your donkey.",
			o("Trade, plus 900 coins", 85, fx("You have a horse now.").horseForDonkey(), fx("It's a very old horse. It moves like a donkey.")).cost(900),
			sure("Keep the donkey", fx("It seems grateful. +10 miles.").miles(10)).def()),
		s("limping", Who.HORSE, "Your horse is limping.",
			o("Stop and check", 90, fx("A pebble. Lose a quarter turn.").move(0.75), fx("A loose shoe. Walking speed until you have horseshoes.").move(0.75).throwShoe()).def(),
			o("Push on", 45, fx("+10 miles.").miles(10), fx("A thrown shoe. Walking speed until you have horseshoes.").throwShoe())),
		s("plasticbag", Who.HORSE, "A plastic bag on the wind.",
			o("Calm it down", 70, NOTHING, fx("Spooked. Lose a turn.").move(0)).def(),
			o("Gallop past", 40, fx("+30 miles.").miles(30), fx("Spooked into Wyoming. Lose a turn and 20 miles.").move(0).miles(-20))),
		s("axle", Who.WAGON, "A strange noise from the axle.",
			o("Stop and tighten it", 85, fx("Lose a quarter turn.").move(0.75), fx("It breaks anyway.").breakWagon()).def(),
			o("Turn up the music", 40, fx("The noise goes away."), fx("The axle snaps. The wagon breaks down.").breakWagon())),
		s("crossing", Who.WAGON, "A river crossing.",
			o("Ford it", 55, fx("Across, for free."), fx("You lose 3 snack packs to the river.").food(-3)),
			o("Caulk the wagon and float it", 60, fx("+20 miles.").miles(20), fx("The wagon breaks down.").breakWagon()).needs(Needs.WAGON),
			o("Pay the ferry, 50 each", 95, fx("Safe."), fx("The ferry is late. Lose half a turn.").move(0.5)).costEach(50).def()),

		// Party drama.
		s("auxcord", Who.PARTY, "Aux cord war: Taylor Swift or a true crime podcast?",
			o("Taylor", 50, fx("The party bonds. +20 miles.").miles(20), fx("Someone sulks. -15% miles.").move(0.85)).def(),
			o("The podcast", 50, fx("The party bonds. +20 miles.").miles(20), fx("Someone sulks. -15% miles.").move(0.85)),
			sure("Play A Really Good Playlist", fx("Peace. +20 miles.").miles(20)).needs(Needs.PLAYLIST)),
		s("cooler", Who.PARTY, "\"Who packed the cooler?\" Nobody packed the cooler.",
			o("Blame someone", 40, fx("They pack it next time. +2 snack packs.").food(2), fx("Drama. -2 snack packs, -20 miles.").food(-2).miles(-20)),
			o("Everyone chips in, 10 coins each", 90, fx("+3 snack packs.").food(3), fx("+1 snack pack.").food(1)).costEach(10).def()),
		s("pop", Who.PARTY, "The pop versus soda debate.",
			o("Say pop", 60, fx("The Minnesotans approve. +10 miles.").miles(10), fx("It escalates. -20 miles.").miles(-20)),
			o("Say soda", 40, fx("You win the argument. +20 miles.").miles(20), fx("Nobody speaks for 40 miles. -30 miles.").miles(-30)),
			o("Say \"Coke\"", 80, fx("Everyone is confused into silence."), fx("-10 miles.").miles(-10)).def()),
		s("ex", Who.PARTY, "Somebody's ex texts the group chat.",
			o("Read it out loud", 45, fx("It's an apology and a 100 coin Venmo.").coins(100), fx("Lose half a turn processing.").move(0.5)),
			o("Leave them on read", 85, fx("+10 miles of power.").miles(10), fx("-10 miles of wondering.").miles(-10)).def()),
		s("syncbangs", Who.PARTY, "Synchronized bangs at a rest stop.",
			o("Everyone trims everyone", 60, fx("You look amazing. +30 miles.").miles(30), fx("One bang is crooked and nobody will say whose. -20 miles.").miles(-20)),
			o("Just touch-ups", 90, fx("+10 miles.").miles(10), fx("-5 miles.").miles(-5)).def()),
		s("holstein", Who.PARTY, "A detour to the World's Largest Holstein gift shop?",
			o("Go", 75, fx("-30 miles, but the next scenario is more likely to go well.").miles(-30).luck(15), fx("Cow hats for everyone. -60 coins.").coins(-60)),
			o("Skip it", 90, NOTHING, fx("Someone brings it up for 200 miles. -10 miles.").miles(-10)).def()),
		s("lastbar", Who.PARTY, "Somebody ate the last bar.",
			o("Investigate", 50, fx("The culprit confesses and buys 3 snack packs.").food(3), fx("Everyone is a suspect. -1 snack pack, -20 miles.").food(-1).miles(-20)),
			o("Let it go", 90, fx("-1 snack pack.").food(-1), fx("-1 snack pack and a lot of feelings.").food(-1).miles(-5)).def()));

	public static final Map<String, Scenario> BY_KEY = ALL.stream().collect(Collectors.toMap(Scenario::key, Function.identity()));
}
