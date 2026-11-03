package com.grove.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Grove Trail's scenarios: the hazards of crossing the frontier in the 1800s, with
 * the odd stylist's opportunity. One is dealt for each turn, always with exactly four
 * options. Each option has a base chance (out of 100) of going well, which
 * {@link TrailOdds#chance} moves for whoever is behind or ahead; 100 means it always goes
 * the good way. Players see only the options, never the odds or outcomes.
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
		/** Provisions this option uses up front (selling them, throwing bacon). It isn't offered without them. */
		public int foodNeeded;
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
		Option usesFood(int n) { foodNeeded = n; return this; }
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
		// The trail.
		s("river", Who.ANY, "You reach a wide, muddy river.",
			o("Ford it", 55, fx("Across with dry boots, somehow. +20 miles.").miles(20), fx("The current takes 3 provisions.").food(-3)),
			o("Build a raft", 60, fx("It floats. +10 miles.").miles(10), fx("The raft was more of a suggestion. Lose a turn.").move(0)),
			o("Pay the ferryman, 40 coins", 95, fx("Safely across."), fx("The ferryman was drunk. Lose half a turn.").move(0.5)).cost(40).def(),
			o("Search upstream for a shallow crossing", 70, fx("Found one. -10 miles.").miles(-10), fx("There isn't one. -40 miles.").miles(-40))),
		s("pond", Who.ANY, "The water barrel is nearly empty. There's a green pond nearby.",
			o("Drink from the pond", 45, fx("Refreshing, if a bit chewy. +10 miles.").miles(10), fx("Dysentery. Lose a turn.").move(0)),
			o("Boil it first", 90, fx("Safe. Lose a quarter turn.").move(0.75), fx("No dry wood anywhere. Lose half a turn.").move(0.5)).def(),
			o("Dig for a spring", 55, fx("Clear, cold water. +20 miles of good spirits.").miles(20), fx("You dig a very nice hole. Lose half a turn.").move(0.5)),
			o("Push on thirsty", 60, fx("You find a creek by sundown. +20 miles.").miles(20), fx("You collapse in the shade. Lose a turn.").move(0))),
		s("cholera", Who.ANY, "A family camped ahead has cholera.",
			o("Help nurse them", 40, fx("They pull through and share 4 provisions.").food(4), fx("You catch it. Lose a turn.").move(0)),
			o("Give them medicine, 50 coins", 80, fx("They recover. The trail feels kinder. Next turn more likely to go well.").luck(15), fx("The medicine was mostly whiskey.")).cost(50),
			o("Camp far upwind", 90, fx("-10 miles.").miles(-10), fx("Far upwind is very far. -20 miles.").miles(-20)).def(),
			o("Leave at once", 70, fx("+10 miles.").miles(10), fx("You leave the cook pot behind. -1 provision.").food(-1))),
		s("buffalo", Who.ANY, "A buffalo herd stretches to the horizon.",
			o("Hunt one", 50, fx("Fresh meat. +6 provisions.").food(6), fx("It hunts you instead. Lose a turn.").move(0)),
			o("Wait for them to pass", 90, fx("Lose half a turn.").move(0.5), fx("It's a very big herd. Lose a turn.").move(0)).def(),
			o("Ride straight through", 35, fx("+30 miles.").miles(30), fx("Stampede. Lose 3 provisions and 20 miles.").food(-3).miles(-20)),
			o("Trade with buffalo hunters, 30 coins", 80, fx("+3 provisions.").food(3), fx("+1 provision of questionable meat.").food(1)).cost(30)),
		s("prairiefire", Who.ANY, "Smoke on the horizon. A prairie fire is heading your way.",
			o("Outrun it", 45, fx("+40 miles.").miles(40), fx("It's faster. Lose 3 provisions and 20 miles.").food(-3).miles(-20)),
			o("Set a backfire", 60, fx("Clever. Nothing of yours burns."), fx("You set your own camp alight. Lose 2 provisions and half a turn.").food(-2).move(0.5)),
			o("Shelter in a creek bed", 90, fx("Damp, but alive. Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def(),
			o("Pray", 30, fx("The wind turns. +10 miles of relief.").miles(10), fx("It doesn't. Lose a turn and 2 provisions.").move(0).food(-2))),
		s("rattlesnake", Who.ANY, "A rattlesnake is coiled in your bedroll.",
			o("Shoot it", 60, fx("Supper. +1 provision.").food(1), fx("You shoot the bedroll. Lose a quarter turn.").move(0.75)),
			o("Wait for it to leave", 85, fx("Lose a quarter turn.").move(0.75), fx("It's very comfortable there. Lose half a turn.").move(0.5)).def(),
			o("Grab it by the tail", 20, fx("It's impressed. +20 miles of bragging.").miles(20), fx("Bitten. Lose a turn.").move(0)),
			sure("Burn the bedroll", fx("A cold night. -10 miles.").miles(-10))),
		s("hail", Who.ANY, "Black clouds, then hail the size of hen's eggs.",
			o("Push on", 40, fx("+20 miles.").miles(20), fx("Battered and bruised. Lose a turn, and any wagon breaks down.").move(0).breakWagon()),
			o("Hunker down and wait", 90, fx("Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def(),
			o("Shelter under a rocky ledge", 70, fx("Lose a quarter turn.").move(0.75), fx("The ledge leaks hail. Lose a turn, and any wagon breaks down.").move(0).breakWagon()),
			o("Race for the trees", 55, fx("+10 miles.").miles(10), fx("Lose half a turn and a provision.").move(0.5).food(-1))),
		s("lost", Who.ANY, "The trail markers have vanished. There is no road out here.",
			o("Follow the sun west", 60, fx("+20 miles.").miles(20), fx("The sun moves. Who knew. -30 miles.").miles(-30)),
			o("Follow the river", 85, fx("-10 miles.").miles(-10), fx("The river wanders. -20 miles.").miles(-20)).def(),
			o("Climb a hill and look", 70, fx("There's the trail. +10 miles.").miles(10), fx("Just more hills. Lose half a turn.").move(0.5)),
			o("Trust your gut", 40, fx("Your gut was right. +40 miles.").miles(40), fx("Your gut was hungry. -50 miles.").miles(-50))),
		s("map", Who.ANY, "A fur trader offers to sell you a map of a shortcut.",
			o("Buy the map, 60 coins", 45, fx("A real shortcut. +60 miles.").miles(60), fx("It's a map of France. -20 miles.").miles(-20)).cost(60),
			o("Haggle him down, 20 coins", 50, fx("Bargain. +40 miles.").miles(40), fx("Insulted, he sells you the wrong half.")).cost(20),
			o("Trade him a haircut", 65, fx("He's a new man and draws you a better map. +40 miles.").miles(40), fx("He hates it and storms off. -20 miles.").miles(-20)),
			sure("Ignore him", NOTHING).def()),
		s("tonic", Who.ANY, "A medicine show rolls into camp selling Dr. Pettibone's Miracle Hair Tonic.",
			o("Buy a bottle, 50 coins", 35, fx("You have never looked better, and it shows in your stride. Double miles.").move(2), fx("It's turpentine.")).cost(50),
			o("Heckle the doctor", 60, fx("The crowd tips you 30 coins.").coins(30), fx("He sets his dog on you. Lose half a turn.").move(0.5)),
			o("Offer to be his barber for a day", 65, fx("Lose a turn, but earn 150 coins.").move(0).coins(150), fx("Lose a turn for 40 coins.").move(0).coins(40)),
			sure("Keep walking", NOTHING).def()),
		s("soldiers", Who.ANY, "A company of soldiers is camped by the trail. Their hair is a disgrace.",
			o("Cut the whole company", 70, fx("Lose a turn, earn 250 coins.").move(0).coins(250), fx("Lose a turn, earn 80 coins.").move(0).coins(80)),
			o("Just the captain", 85, fx("+60 coins.").coins(60), fx("He wanted a trim, not a removal. -20 coins.").coins(-20)),
			o("Sell them 2 provisions", 60, fx("+80 coins.").food(-2).coins(80), fx("They haggle hard. +20 coins.").food(-2).coins(20)).usesFood(2),
			sure("Move on", NOTHING).def()),
		s("wolves", Who.ANY, "Wolves circle camp after dark.",
			o("Keep the fire high all night", 85, fx("They give up by dawn."), fx("They make off with 2 provisions.").food(-2)).def(),
			o("Fire into the dark", 55, fx("They scatter."), fx("You hit the coffee pot. -1 provision.").food(-1)),
			o("Throw them some bacon", 75, fx("They take it and go. -1 provision.").food(-1), fx("They want more. -3 provisions.").food(-3)).usesFood(1),
			o("Howl back", 25, fx("They're confused and leave. +10 miles of confidence.").miles(10), fx("They are not confused. -3 provisions.").food(-3))),
		s("dysentery", Who.ANY, "You have dysentery.",
			o("Rest a day", 85, fx("Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def(),
			o("Drink the castor oil", 50, fx("Cured. Unpleasantly."), fx("You have died of dysentery. Just kidding. Lose a turn.").move(0)),
			o("Keep walking", 40, fx("Mind over matter."), fx("Matter wins. Lose a turn.").move(0)),
			o("See the barber-surgeon, 40 coins", 75, fx("Cured."), fx("He bleeds you anyway. Lose half a turn.").move(0.5)).cost(40)),
		s("abandoned", Who.ANY, "An abandoned wagon by the trail, half buried in dust.",
			o("Search it", 55, fx("A good spare wheel.").wheels(1), fx("A skunk lives there now. Lose half a turn.").move(0.5)),
			o("Take its provisions", 60, fx("+3 provisions.").food(3), fx("Spoiled. You're sick. Lose half a turn.").move(0.5)),
			o("Burn it for a warm night", 80, fx("A cozy night. Next turn more likely to go well.").luck(15), fx("The smoke draws mosquitoes. -10 miles.").miles(-10)),
			sure("Leave it be", NOTHING).def()),
		s("mosquitoes", Who.ANY, "Clouds of mosquitoes in the river bottoms.",
			o("Smear on mud", 75, NOTHING, fx("-10 miles of scratching.").miles(-10)).def(),
			o("Push through fast", 55, fx("+20 miles.").miles(20), fx("-20 miles of scratching.").miles(-20)),
			o("Burn green branches for smoke", 70, NOTHING, fx("Now you're smoked and bitten. Lose a quarter turn.").move(0.75)),
			o("Buy netting from a trader, 20 coins", 90, fx("+10 miles in comfort.").miles(10), NOTHING).cost(20)),
		s("gold", Who.ANY, "Prospectors say there's gold in a creek nearby.",
			o("Pan for a day", 30, fx("Gold! Lose a turn, gain 300 coins.").move(0).coins(300), fx("Fool's gold. Lose a turn, gain 5 coins.").move(0).coins(5)),
			o("Sell them 2 provisions", 65, fx("+100 coins.").food(-2).coins(100), fx("+30 coins.").food(-2).coins(30)).usesFood(2),
			o("Cut their hair for gold dust", 60, fx("Lose half a turn, gain 120 coins.").move(0.5).coins(120), fx("Lose half a turn, gain 30 coins.").move(0.5).coins(30)),
			sure("Ignore it", fx("+5 miles of good sense.").miles(5)).def()),
		s("graves", Who.ANY, "A row of fresh graves by the trail. The markers all say \"Drank the water.\"",
			sure("Read every marker", fx("Sobering. Next turn more likely to go well. Lose a quarter turn.").luck(15).move(0.75)),
			o("Hurry past", 80, fx("+10 miles.").miles(10), fx("You trip over a marker. -10 miles.").miles(-10)).def(),
			o("Sing a hymn for them", 70, fx("Next turn more likely to go well.").luck(15), fx("Lose a quarter turn.").move(0.75)),
			o("Test the water yourself", 20, fx("It's fine now. +10 miles.").miles(10), fx("It was not fine. Lose a turn.").move(0))),
		s("thieves", Who.ANY, "Horse thieves are said to be working this stretch.",
			o("Stand watch all night", 80, fx("Tired, but nothing's missing. -10 miles.").miles(-10), fx("You fell asleep. -40 coins.").coins(-40)).def(),
			o("Hide your coin in your boot", 70, NOTHING, fx("They check boots. -60 coins.").coins(-60)),
			o("Set a trap", 45, fx("You catch one. The reward is 150 coins.").coins(150), fx("You catch yourself. Lose half a turn.").move(0.5)),
			o("Travel by night instead", 50, fx("+30 miles.").miles(30), fx("You stumble into a gully. Lose half a turn and a provision.").move(0.5).food(-1))),
		s("trade", Who.ANY, "Another party of emigrants offers to trade.",
			o("Buy provisions, 40 coins", 90, fx("+4 provisions.").food(4), fx("+2 provisions.").food(2)).cost(40).def(),
			o("Sell 2 provisions", 80, fx("+60 coins.").food(-2).coins(60), fx("+20 coins.").food(-2).coins(20)).usesFood(2),
			o("Offer haircuts for provisions", 70, fx("Lose half a turn, gain 5 provisions.").move(0.5).food(5), fx("Lose half a turn, gain 1 provision.").move(0.5).food(1)),
			sure("Swap stories by the fire", fx("+10 miles of good cheer.").miles(10))),
		s("duststorm", Who.ANY, "A dust storm blots out the sun.",
			o("Wait it out", 90, fx("Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def(),
			o("Rope everyone together and walk", 50, fx("+10 miles.").miles(10), fx("-20 miles.").miles(-20)),
			o("Navigate by compass", 60, fx("+20 miles.").miles(20), fx("The compass was upside down. -30 miles.").miles(-30)),
			o("Sing loudly", 35, fx("Everyone joins in. Next turn more likely to go well.").luck(15), fx("A mouthful of dust. Lose half a turn.").move(0.5))),
		s("brokenwheel", Who.ANY, "An emigrant family asks for help with a broken wheel.",
			o("Help them fix it", 65, fx("They share 2 provisions and a blessing.").food(2).luck(10), fx("It takes all day. Lose a turn.").move(0)),
			o("Sell them your tools", 70, fx("+80 coins.").coins(80), fx("+20 coins.").coins(20)),
			o("Take their spare wheel \"for safekeeping\"", 40, fx("You have a spare wheel now.").wheels(1), fx("They chase you off. -20 miles.").miles(-20)),
			sure("Point them toward the fort", NOTHING).def()),
		s("lice", Who.ANY, "Everyone in camp has lice.",
			o("Shave every head in camp", 70, fx("Problem solved. They tip you 50 coins.").coins(50), fx("Nobody forgives you. -20 miles.").miles(-20)),
			o("Kerosene wash", 60, NOTHING, fx("Lose a quarter turn airing out.").move(0.75)),
			o("Ignore it", 80, fx("-5 miles of itching.").miles(-5), fx("-15 miles of itching.").miles(-15)).def(),
			o("Sell your fine-tooth comb", 75, fx("+40 coins.").coins(40), fx("+10 coins.").coins(10))),
		s("wetgoods", Who.ANY, "Half your supplies got soaked crossing a creek.",
			o("Dry everything out", 90, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def(),
			o("Eat the wet stuff first", 70, NOTHING, fx("You're sick. Lose half a turn.").move(0.5)).usesFood(1),
			o("Sell it cheap at the next camp", 60, fx("+30 coins.").food(-2).coins(30), fx("+5 coins.").food(-2).coins(5)).usesFood(2),
			sure("Leave it and travel light", fx("-2 provisions, +10 miles.").food(-2).miles(10))),
		s("fourth", Who.ANY, "Independence Day. A camp of emigrants is celebrating.",
			o("Join the dance", 70, fx("A grand time. Next turn more likely to go well.").luck(15), fx("You step on the judge. Lose half a turn.").move(0.5)),
			o("Enter the shooting contest, 20 coins", 40, fx("You win the 150 coin purse.").coins(150), fx("You lose.")).cost(20),
			o("Do hair for the festivities", 75, fx("Lose half a turn, earn 120 coins.").move(0.5).coins(120), fx("Lose half a turn, earn 40 coins.").move(0.5).coins(40)),
			sure("Keep moving while they nap", fx("+10 miles.").miles(10)).def()),
		s("scout", Who.ANY, "A grizzled scout offers to guide you.",
			o("Hire him, 100 coins", 75, fx("He knows every shortcut. +60 miles.").miles(60), fx("He gets you lost. -20 miles.").miles(-20)).cost(100),
			o("Ask for directions only", 60, fx("+20 miles.").miles(20), fx("He lies for fun. -20 miles.").miles(-20)),
			o("Trade him a shave for directions", 65, fx("+40 miles.").miles(40), fx("He's touchy about his beard. -10 miles.").miles(-10)),
			sure("Decline", NOTHING).def()),
		s("rain", Who.ANY, "It rains for three days straight.",
			o("Wait it out", 90, fx("Lose half a turn.").move(0.5), fx("Lose a turn.").move(0)).def(),
			o("Push through the mud", 45, fx("+10 miles.").miles(10), fx("Stuck in the mud to your knees. Lose a turn.").move(0)),
			o("Build a proper shelter", 70, fx("Lose a quarter turn.").move(0.75), fx("It leaks. Lose half a turn.").move(0.5)),
			sure("Wash everyone's hair in it", fx("Everyone feels human again. Next turn more likely to go well. Lose half a turn.").luck(15).move(0.5))),
		s("grasshoppers", Who.ANY, "A swarm of grasshoppers descends on camp.",
			o("Cover the provisions", 85, NOTHING, fx("They get in anyway. -1 provision.").food(-1)).def(),
			o("Run", 60, fx("+20 miles.").miles(20), fx("They follow. -2 provisions.").food(-2)),
			o("Eat them", 35, fx("Crunchy. +3 provisions.").food(3), fx("Not crunchy. Lose half a turn.").move(0.5)),
			o("Fight them off", 40, fx("+10 miles of glory.").miles(10), fx("They win. -3 provisions.").food(-3))),
		s("homestead", Who.ANY, "A homesteader's wife needs her hair done for church on Sunday.",
			o("Do her hair", 75, fx("+100 coins.").coins(100), fx("The curling iron was too hot. -20 coins.").coins(-20)),
			o("Ask for provisions instead", 80, fx("+3 provisions.").food(3), fx("+1 provision.").food(1)),
			o("Ask the way west", 90, fx("+20 miles.").miles(20), NOTHING).def(),
			sure("Keep riding", NOTHING)),
		s("grizzly", Who.ANY, "A grizzly bear stands between you and the way west.",
			o("Back away slowly", 85, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def(),
			o("Make yourself big", 55, fx("It leaves."), fx("It doesn't. Lose 3 provisions.").food(-3)),
			o("Play dead", 45, NOTHING, fx("It plays along for hours. Lose a turn.").move(0)),
			o("Throw it the bacon", 75, fx("It takes it. -2 provisions.").food(-2), fx("It wants all of it. -4 provisions.").food(-4)).usesFood(2)),
		s("dog", Who.ANY, "A stray dog has been following you for miles.",
			o("Adopt it", 80, fx("A loyal companion. Better luck the rest of the way. It eats a provision a turn.").puppy(), fx("It runs off with a provision.").food(-1)),
			o("Shoo it away", 90, NOTHING, fx("It takes it personally. -10 miles.").miles(-10)).def(),
			o("Give it a trim", 60, fx("It looks dashing. A settler pays 40 coins for it.").coins(40), fx("It bites. Lose a quarter turn.").move(0.75)),
			sure("Feed it once", fx("-1 provision. It wanders off happy.").food(-1)).usesFood(1)),

		// Only for one way of getting around.
		s("boots", Who.WALK, "Your boots have worn clean through.",
			o("Wrap your feet in rags", 75, NOTHING, fx("-40% miles.").move(0.6)).def(),
			o("Buy moccasins from a trader, 30 coins", 90, fx("+10 miles in comfort.").miles(10), NOTHING).cost(30),
			o("Walk barefoot", 35, fx("Tough as leather. +10 miles.").miles(10), fx("Lose a turn.").move(0)),
			o("Hitch a ride on a freight wagon", 55, fx("+50 miles.").miles(50), fx("He's going the other way. -30 miles.").miles(-30))),
		s("freighter", Who.WALK, "A freighter offers you a seat in exchange for a week of cooking.",
			o("Accept", 70, fx("+60 miles.").miles(60), fx("His mules hate you. +5 miles.").miles(5)),
			o("Offer haircuts instead", 65, fx("+60 miles.").miles(60), fx("+10 miles.").miles(10)),
			o("Ride on the tailgate without asking", 35, fx("+60 miles.").miles(60), fx("Thrown off. Lose half a turn.").move(0.5)),
			sure("Decline politely", NOTHING).def()),
		s("stubborn", Who.DONKEY, "Your donkey has stopped and will not move.",
			o("Offer it carrots", 95, fx("It moves. -1 carrots.").carrots(-1), fx("It eats the carrots and stays. Lose a turn.").carrots(-1).move(0)).needs(Needs.CARROTS),
			o("Push it", 35, fx("It moves, offended."), fx("It sits on you. Lose a turn.").move(0)),
			o("Wait it out", 85, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)).def(),
			o("Sing to it", 50, fx("It loves the high notes. +10 miles.").miles(10), fx("It hates the high notes. Lose a turn.").move(0))),
		s("hat", Who.DONKEY, "Your donkey ate a missionary's hat.",
			o("Apologize", 90, fx("Forgiven. Lose a quarter turn.").move(0.75), fx("He wants 20 coins.").coins(-20)).def(),
			sure("Pay for the hat, 30 coins", fx("Square.")).cost(30),
			o("Blame another donkey", 40, NOTHING, fx("There is no other donkey. -40 coins.").coins(-40)),
			o("Offer him a haircut to make up for it", 70, fx("He tips you 30 coins.").coins(30), fx("He wanted the hat. -20 coins.").coins(-20))),
		s("rancher", Who.DONKEY, "A rancher's daughter offers to trade you a horse for your donkey.",
			o("Trade, plus 900 coins", 85, fx("You have a horse now.").horseForDonkey(), fx("It's a very old horse. It moves like a donkey.")).cost(900),
			o("Haggle, 600 coins", 40, fx("Deal. You have a horse now.").horseForDonkey(), fx("She keeps the coins and the horse. Lesson learned.")).cost(600),
			o("Offer a season of haircuts", 35, fx("She accepts. You have a horse now.").horseForDonkey(), fx("She laughs. Lose half a turn.").move(0.5)),
			sure("Keep the donkey", fx("It seems grateful. +10 miles.").miles(10)).def()),
		s("limping", Who.HORSE, "Your horse is limping.",
			o("Stop and check its hooves", 90, fx("A pebble. Lose a quarter turn.").move(0.75), fx("A loose shoe. Walking speed until you have horseshoes.").move(0.75).throwShoe()).def(),
			o("Push on", 45, fx("+10 miles.").miles(10), fx("A thrown shoe. Walking speed until you have horseshoes.").throwShoe()),
			sure("Rest it a day", fx("Lose a turn. It's fine.").move(0)),
			o("Try a poultice", 60, NOTHING, fx("It smells terrible and doesn't help. Lose half a turn.").move(0.5))),
		s("tumbleweed", Who.HORSE, "A tumbleweed spooks your horse.",
			o("Calm it down", 70, NOTHING, fx("Spooked anyway. Lose a turn.").move(0)).def(),
			o("Gallop past", 40, fx("+30 miles.").miles(30), fx("Bolted the wrong way. Lose a turn and 20 miles.").move(0).miles(-20)),
			o("Get down and lead it", 90, fx("Lose a quarter turn.").move(0.75), fx("Lose half a turn.").move(0.5)),
			o("Braid its mane to soothe it", 55, fx("It's never been so calm. +10 miles.").miles(10), fx("It is not a braid horse. Lose half a turn.").move(0.5))),
		s("axle", Who.WAGON, "A terrible creaking from the wagon axle.",
			o("Stop and tighten it", 85, fx("Lose a quarter turn.").move(0.75), fx("It breaks anyway.").breakWagon()).def(),
			o("Ignore it", 40, fx("It goes away."), fx("The axle snaps. The wagon breaks down.").breakWagon()),
			o("Lighten the load, dump 2 provisions", 75, fx("The creak stops. -2 provisions.").food(-2), fx("It breaks anyway, and you're 2 provisions lighter.").food(-2).breakWagon()).usesFood(2),
			o("Grease it with bacon fat", 60, fx("Smooth as butter. -1 provision.").food(-1), fx("Now it creaks and smells. -1 provision.").food(-1)).usesFood(1)),
		s("crossing", Who.WAGON, "A deep, fast river. The wagon will have to cross.",
			o("Ford it", 55, fx("Across, for free."), fx("You lose 3 provisions to the river.").food(-3)),
			o("Caulk the wagon and float it", 60, fx("+20 miles.").miles(20), fx("It sinks halfway. The wagon breaks down.").breakWagon()),
			o("Pay the ferry, 50 each", 95, fx("Safely across."), fx("The ferry is late. Lose half a turn.").move(0.5)).costEach(50).def(),
			o("Find a shallower crossing", 70, fx("-10 miles.").miles(-10), fx("-40 miles.").miles(-40))),
		s("hill", Who.WAGON, "A hill so steep the wagon could roll away.",
			o("Lower it down with ropes", 85, fx("Lose half a turn.").move(0.5), fx("A rope snaps. The wagon breaks down.").breakWagon()).def(),
			o("Ride it down", 30, fx("Exhilarating. +30 miles.").miles(30), fx("Less exhilarating. The wagon breaks down.").breakWagon()),
			o("Unload everything and carry it", 90, fx("Lose a turn.").move(0), fx("Lose a turn and a provision.").move(0).food(-1)),
			o("Go around", 80, fx("-30 miles.").miles(-30), fx("-50 miles.").miles(-50))),

		// Party drama.
		s("whichway", Who.PARTY, "The party argues over which way to go.",
			o("Put it to a vote", 60, fx("Democracy wins. +10 miles.").miles(10), fx("It's a tie. Lose half a turn.").move(0.5)),
			o("Flip a coin", 50, fx("+20 miles.").miles(20), fx("Best two out of three. Lose half a turn.").move(0.5)),
			o("Let the oldest decide", 70, NOTHING, fx("Nobody admits to being oldest. -10 miles.").miles(-10)).def(),
			sure("Play the fiddle until everyone agrees", fx("Peace. +20 miles.").miles(20)).needs(Needs.PLAYLIST)),
		s("coffee", Who.PARTY, "Somebody forgot to pack the coffee.",
			o("Blame someone", 40, fx("They make it right. +2 provisions.").food(2), fx("A feud. -2 provisions and 20 miles.").food(-2).miles(-20)),
			o("Everyone chips in, 10 coins each", 90, fx("+3 provisions from the next camp.").food(3), fx("+1 provision.").food(1)).costEach(10).def(),
			o("Brew chicory root", 55, fx("Close enough. +10 miles.").miles(10), fx("Not close enough. -10 miles.").miles(-10)),
			sure("Go without", fx("Everyone is grim. -10 miles.").miles(-10))),
		s("bacon", Who.PARTY, "Somebody ate most of the bacon.",
			o("Investigate", 50, fx("The culprit confesses and buys 3 provisions.").food(3), fx("Everyone is a suspect. -1 provision, -20 miles.").food(-1).miles(-20)),
			o("Let it go", 90, fx("-1 provision.").food(-1), fx("-1 provision and a lot of feelings.").food(-1).miles(-5)).def(),
			o("Accuse the dog", 60, fx("Everyone agrees. Peace."), fx("There is no dog. -10 miles.").miles(-10)),
			o("Ration everything from now on", 70, fx("+2 provisions saved.").food(2), fx("Mutiny. Lose half a turn.").move(0.5)).usesFood(1)),
		s("trims", Who.PARTY, "Everyone trims each other's hair by the campfire.",
			o("Everyone trims everyone", 60, fx("You look amazing. +30 miles.").miles(30), fx("One part is crooked and nobody will say whose. -20 miles.").miles(-20)),
			o("Just a little off the ends", 90, fx("+10 miles.").miles(10), fx("-5 miles.").miles(-5)).def(),
			o("Matching hairstyles", 45, fx("The party has never been closer. Next turn more likely to go well.").luck(20), fx("Nobody likes it. -20 miles.").miles(-20)),
			sure("Sell the clippings to a wigmaker", fx("+30 coins.").coins(30))));

	public static final Map<String, Scenario> BY_KEY = ALL.stream().collect(Collectors.toMap(Scenario::key, Function.identity()));
}
