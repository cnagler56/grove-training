# The Grove Trail: design note (draft for review)

An Oregon Trail style game set in the 1800s. Stylists earn coins from their Salon Ultimate service sales and
spend them on provisions, animals and wagons to cross the frontier from Maple Grove, Minnesota Territory to the
Pacific at Haystack Rock. There are no roads: just rivers, forts and trading posts between them.
This lives only on the `oregon-trail` branch.

## Basics

- A manager creates a trail game the way they create a contest: name, start date, length (30 to 50 turns,
  default 40), prize text, optional handicap, players. Receptionists and customers can't play.
- **A new turn unlocks at every Salon Ultimate check** (8 am, 12, 4 pm, 8 pm), so a 40 turn game runs about 10
  days. Each player (or party) plays their own turns: pick an option, press **Go**, and see what happened right
  away. Unplayed turns wait; after the last check, anything still unplayed goes with the safe choice.
- **Coins:** $1 of service sales = 1 coin, plus 25 per treatment, 20 per color, 15 per wax (bonuses set when
  the game is created), counted at every check. Everyone also starts with 200 coins and 5 provisions.
- **Handicap (optional):** the contest handicaps reused. NONE, HOURS (fewer hours earns more per dollar),
  RATE (lower usual sales per hour earns more), BOTH. Coins are multiplied by worth / 100 (100 to 200),
  from the 4 weeks before the start.
- **Winning:** the party that reaches the Pacific in the fewest turns wins and splits the prize. When the game
  ends, everyone else is ranked by miles. People who arrive early wait on the beach and show as "At the Pacific".

## A turn (when you press Go)

Every turn shows exactly four choices, as plain labels: no odds, no hint of the upside or downside, and
nothing preselected. Go stays off until you pick one.

1. Your pick plays out: roll the option's odds (moved by the rubber band) and apply the good or bad outcome.
   Only now do you see what happened. In a party, whoever presses Go decides for everyone.
2. Everyone eats 1 provision. Going without gets worse every turn until there's food: 1 turn hungry (half
   speed), 2 to 3 weak (half speed, 10 points worse luck), 4 to 5 starving (a quarter speed, 20 points worse
   luck), 6 or more too weak to travel at all. A traveling trader, a lucky scenario, or a kind stranger who
   shares provisions is the way out.
3. Move west: speed of the party's travel mode, minus any delays.
4. Passing a fort with a trading post opens it until your next turn. Otherwise a traveling trader turns
   up 15% of the time. There is nowhere else to buy (a wheelwright for a broken wagon can be sent for anywhere).
5. The next turn is dealt: usually a scenario where all four choices work for you right now (no selling or
   throwing provisions you don't have; no repeats until you've seen them all); sometimes a meeting
   with someone on the same stretch of trail (see Meeting someone).
6. Landmarks: a line in your log. The first party to each landmark, and everyone who reaches the Pacific,
   is posted on the Team Board. Only the Pacific gets a push notification.

At each check, coins are counted and one more turn unlocks for everyone.

## Map and landmarks (1,870 trail miles)

A drawn map: the overland route plotted from the landmarks' real coordinates as a dashed line (no roads),
with faint territory names (Minnesota, Dakota Territory, Montana Territory, Idaho Territory, Washington
Territory, Oregon). You are a labeled dot ("You") and the ground you've covered is colored in; everyone else is
a dot with their first name. Under the map: your mile, the last landmark, and how far to the next one.

| Miles | Landmark | Trading post |
|---|---|---|
| 0 | Maple Grove, Minnesota Territory | yes, before you leave |
| 70 | The Crow River | |
| 150 | Otter Tail Lake | |
| 235 | Fort Abercrombie on the Red River | yes |
| 330 | The Sheyenne River | |
| 420 | The James River | |
| 560 | Fort Berthold on the Missouri | yes |
| 690 | Fort Union | yes |
| 820 | The Yellowstone River | |
| 960 | Pompeys Pillar (Clark carved his name here in 1806) | |
| 1130 | Three Forks of the Missouri | |
| 1200 | The Continental Divide | |
| 1290 | Hell Gate | yes |
| 1410 | The Coeur d'Alene Mission | yes |
| 1460 | Spokane Falls | |
| 1600 | Fort Walla Walla | yes |
| 1720 | The Dalles | |
| 1780 | The Cascade Rapids | |
| 1810 | Fort Vancouver | yes |
| 1870 | The Pacific at Haystack Rock | |

## Travel modes

Speeds are miles per turn for a 40 turn game and scale with length (a 30 turn game moves 40/30 as fast),
so the same mode always takes the same share of the game.

| Mode | Cost | Speed | Turns to the Pacific | Its own risks |
|---|---|---|---|---|
| Walking | free | 28 | about 67 (won't make it) | Boots wear through; rides from freighters |
| Donkey | 800 | 40 | about 47 | Stops and won't move (carrots help, 60); eats missionaries' hats |
| Horse | 2,000 | 52 | about 36 | Throws a shoe (walking speed until you buy horseshoes, 150); spooked by tumbleweeds |
| Covered wagon with oxen (seats 4) | 6,000 | 64 | about 29 | Breaks down (2% a turn on its own, plus risky scenario options; doubled on Grueling): stuck until a wheelwright fixes it for 1,200, or instantly with a spare wheel (400) |

Pace is always Steady for now, to keep the page simple. Leisurely (x0.8, safer) and Grueling (x1.25, eats
double, riskier) still exist in `TrailOdds` if we want a pace choice back.

Anyone in a party not riding in the wagon needs their own animal; without a wagon the party moves at its
slowest member's pace. The wagon can be abandoned (no refund) if nobody can pay to fix it.

Trading posts (at the forts in the table above, or a passing trader): provisions (12 each), donkey, horse,
covered wagon, spare wagon wheel, horseshoes, carrots, and a fiddle (100, settles one argument about which way
to go). A wheelwright (1,200) can be sent for anywhere.

Prices above assume a stylist averages about $800 a day in service sales. I'd like to scale every price by
the players' real average over the 4 weeks before the start (the same window handicaps use), so a wagon is
always "about a week and a half of one person's sales" and needs pooling. Fallback: the numbers above.

## Rubber-band odds

All tuning numbers live in one class (`TrailOdds`).

Every scenario is a multiple choice. Each option has its own base chance of turning out well (drinking
from a river is riskier than drinking the emergency LaCroix), and the rubber band moves that chance.

- Behind = (average miles of everyone still on the trail - my miles) / trail length.
- Chance the chosen option turns out well = its base chance + 150 x behind (in points), plus karma and pace.
- The total shift is kept within 20 points of the base, and the result between 10% and 90%.
- Pace: Leisurely +5, Steady 0, Grueling -5. Karma: see Stealing and helping.
- Example: "Drink from the river" has a 60% base. 190 miles behind the pack (10% of the trail) makes it
  75%; 190 miles ahead makes it 45%.

Players see each option and what could happen, but never the percentages; it should feel like luck,
with the trailing players quietly getting the better luck.

## Going solo or in a party

- You can only team up through a meeting on the trail (see Meeting someone): no invites from across the map.
  The party sets off from the **rearmost member's spot** (the one in front "goes back for them"). Up to 4 people.
- **Pooled coins:** any member can buy for the party; the cost is split evenly across members who have
  coins, and a log shows who paid what for which purchase.
- **Pros:** share one wagon (the main reason to join), pooled buying power, party-only scenarios,
  and choices can be answered by whoever is awake.
- **Cons:** food x members, a bad outcome hits everyone, slowest pace without a wagon, party drama scenarios,
  and the prize is split.
- **Leaving:** anyone can leave at any time. They keep their unspent coins and take an equal share of the
  food. Shared purchases (wagon, spare wheel) stay with the party; animals belong to whoever rides them,
  so yours always leaves with you (animals are bought by the rider alone, never split). After leaving,
  you can't join another party for 4 turns ("needs some space"). The last person left keeps everything.
- **Joining:** one side has to be traveling alone; two parties don't merge. If both had wagons,
  the newcomer sells theirs back for half price.

## Stealing and helping

All the numbers below live in `TrailOdds` with the rubber-band ones.

**Karma.** Every theft gives the thief -5 points on their good-outcome chance for the next 4 turns.
Every act of help gives the helper +5 for 4 turns. Karma stacks up to plus or minus 15, and is still kept
inside the rubber-band limits (20 points either side of the base, 10% to 90%).

### Stealing from your own party

- **How often:** after each turn the party plays, each member of a party of 2 or more has a 6% chance of being
  offered "Skim off the top." Nobody gets the offer in the first 3 turns, more than once every 5 turns, or
  in their first 2 turns after joining. In a 3 person party over 40 turns that works out to roughly 6
  offers in total. It's a private choice: only the thief sees it, and ignoring it means no.
- **What can be taken (pick one):** coins (up to 15% of the party's pooled coins, at most 300, taken from
  the other members evenly), up to 3 provisions, or the spare wheel or horseshoes. Food and parts are
  sold on the spot ("to a passing freighter") for their shop price in coins. Never the wagon or an animal: they
  don't fit in a pocket.
- **Does anyone find out:** the party log always gets a line like "Someone took 120 coins from the
  party. The jerky is also missing." and every other member gets a push notification. 40% of the time
  the thief is caught, and the line names them ("caught with crumbs on their cape").
- **Afterwards:**
  - A caught thief gets a **Sticky Fingers** label beside their name on the map for the rest of the game,
    and must give back half of what they took.
  - Each victim gets a one-time **Get Even** choice for the next 3 turns: 60% to take back double what
    was stolen from them, 40% to get caught themselves (and earn their own Sticky Fingers).
  - Any victim can **vote the thief out**: the thief leaves the party as if they'd left on their own, but
    with no share of the food and the 4 turn "needs some space" wait doubled to 8.
  - An uncaught thief who is stolen from later gets no sympathy text, just "Karma's a stylist too."

### Meeting someone on the trail

- **Who meets:** after you move, if another party is on the same stretch of trail (past the same landmark,
  not counting the starting line), there's a 40% chance your next turn is a meeting with them instead of a
  scenario. The same two don't meet again for 6 turns.
- **Your options:** always four, like any turn: tip your hat and ride on (the default), share your
  provisions (up to 5), rob them in the night (up to 20% of their coins, at most 250), and a fourth: ask them
  to travel together when that's on offer, otherwise help mend their wagon if it's broken down (75% of a
  wheelwright), otherwise lend them some coin (up to 300 of yours). It plays out when you press Go; they find
  out by notification.
- **Traveling together:** about a third of meetings (35%) also offer "Ask them to travel together", and only
  when one of you is alone, there's room (4 at most), and nobody needs space. They answer yes or no on their
  own time; it only works if you're still on the same stretch. The one traveling alone joins the other,
  at the rearmost spot.
- **Chance a theft works**, so the rich can't snowball:
  - Worth = coins + the shop value of food and spare parts (animals and wagons don't count).
  - Success = 50% + 40 x (their worth - your worth) / (their worth + your worth), kept between 25% and 70%.
  - If the thief is in the top third of the standings, the haul is halved ("you felt bad about it").
  - Stealing from someone with a broken-down wagon is 10 points less likely to work.
- **If it works:** the haul moves over and the log says who robbed whom. **If not:** the thief gets Sticky
  Fingers, pays the target half of what they tried to take, and loses half a turn explaining themselves to a
  territorial marshal.
- **Helping** earns a Good Samaritan label for 6 turns, karma, and a little luck on your next turn.

## Scenarios (44)

One is dealt for each turn (unless a meeting takes its place), always with four choices. Players see only
the four labels: no odds, no hint of what could happen, nothing preselected. The odds below are the base
chance each choice goes well, before the rubber band moves it. (safe) marks what happens when nobody picks.
Outcomes are in `TrailScenarios.java`.

**The trail**
- **You reach a wide, muddy river.** Ford it [55%] / Build a raft [60%] / Pay the ferryman, 40 coins [95%] (safe) / Search upstream for a shallow crossing [70%]
- **The water barrel is nearly empty. There's a green pond nearby.** Drink from the pond [45%] / Boil it first [90%] (safe) / Dig for a spring [55%] / Push on thirsty [60%]
- **A family camped ahead has cholera.** Help nurse them [40%] / Give them medicine, 50 coins [80%] / Camp far upwind [90%] (safe) / Leave at once [70%]
- **A buffalo herd stretches to the horizon.** Hunt one [50%] / Wait for them to pass [90%] (safe) / Ride straight through [35%] / Trade with buffalo hunters, 30 coins [80%]
- **Smoke on the horizon. A prairie fire is heading your way.** Outrun it [45%] / Set a backfire [60%] / Shelter in a creek bed [90%] (safe) / Pray [30%]
- **A rattlesnake is coiled in your bedroll.** Shoot it [60%] / Wait for it to leave [85%] (safe) / Grab it by the tail [20%] / Burn the bedroll [always]
- **Black clouds, then hail the size of hen's eggs.** Push on [40%] / Hunker down and wait [90%] (safe) / Shelter under a rocky ledge [70%] / Race for the trees [55%]
- **The trail markers have vanished. There is no road out here.** Follow the sun west [60%] / Follow the river [85%] (safe) / Climb a hill and look [70%] / Trust your gut [40%]
- **A fur trader offers to sell you a map of a shortcut.** Buy the map, 60 coins [45%] / Haggle him down, 20 coins [50%] / Trade him a haircut [65%] / Ignore him [always] (safe)
- **A medicine show rolls into camp selling Dr. Pettibone's Miracle Hair Tonic.** Buy a bottle, 50 coins [35%] / Heckle the doctor [60%] / Offer to be his barber for a day [65%] / Keep walking [always] (safe)
- **A company of soldiers is camped by the trail. Their hair is a disgrace.** Cut the whole company [70%] / Just the captain [85%] / Sell them 2 provisions [60%] / Move on [always] (safe)
- **Wolves circle camp after dark.** Keep the fire high all night [85%] (safe) / Fire into the dark [55%] / Throw them some bacon [75%] / Howl back [25%]
- **You have dysentery.** Rest a day [85%] (safe) / Drink the castor oil [50%] / Keep walking [40%] / See the barber-surgeon, 40 coins [75%]
- **An abandoned wagon by the trail, half buried in dust.** Search it [55%] / Take its provisions [60%] / Burn it for a warm night [80%] / Leave it be [always] (safe)
- **Clouds of mosquitoes in the river bottoms.** Smear on mud [75%] (safe) / Push through fast [55%] / Burn green branches for smoke [70%] / Buy netting from a trader, 20 coins [90%]
- **Prospectors say there's gold in a creek nearby.** Pan for a day [30%] / Sell them 2 provisions [65%] / Cut their hair for gold dust [60%] / Ignore it [always] (safe)
- **A row of fresh graves by the trail. The markers all say "Drank the water."** Read every marker [always] / Hurry past [80%] (safe) / Sing a hymn for them [70%] / Test the water yourself [20%]
- **Horse thieves are said to be working this stretch.** Stand watch all night [80%] (safe) / Hide your coin in your boot [70%] / Set a trap [45%] / Travel by night instead [50%]
- **Another party of emigrants offers to trade.** Buy provisions, 40 coins [90%] (safe) / Sell 2 provisions [80%] / Offer haircuts for provisions [70%] / Swap stories by the fire [always]
- **A dust storm blots out the sun.** Wait it out [90%] (safe) / Rope everyone together and walk [50%] / Navigate by compass [60%] / Sing loudly [35%]
- **An emigrant family asks for help with a broken wheel.** Help them fix it [65%] / Sell them your tools [70%] / Take their spare wheel "for safekeeping" [40%] / Point them toward the fort [always] (safe)
- **Everyone in camp has lice.** Shave every head in camp [70%] / Kerosene wash [60%] / Ignore it [80%] (safe) / Sell your fine-tooth comb [75%]
- **Half your supplies got soaked crossing a creek.** Dry everything out [90%] (safe) / Eat the wet stuff first [70%] / Sell it cheap at the next camp [60%] / Leave it and travel light [always]
- **Independence Day. A camp of emigrants is celebrating.** Join the dance [70%] / Enter the shooting contest, 20 coins [40%] / Do hair for the festivities [75%] / Keep moving while they nap [always] (safe)
- **A grizzled scout offers to guide you.** Hire him, 100 coins [75%] / Ask for directions only [60%] / Trade him a shave for directions [65%] / Decline [always] (safe)
- **It rains for three days straight.** Wait it out [90%] (safe) / Push through the mud [45%] / Build a proper shelter [70%] / Wash everyone's hair in it [always]
- **A swarm of grasshoppers descends on camp.** Cover the provisions [85%] (safe) / Run [60%] / Eat them [35%] / Fight them off [40%]
- **A homesteader's wife needs her hair done for church on Sunday.** Do her hair [75%] / Ask for provisions instead [80%] / Ask the way west [90%] (safe) / Keep riding [always]
- **A grizzly bear stands between you and the way west.** Back away slowly [85%] (safe) / Make yourself big [55%] / Play dead [45%] / Throw it the bacon [75%]
- **A stray dog has been following you for miles.** Adopt it [80%] / Shoo it away [90%] (safe) / Give it a trim [60%] / Feed it once [always]

**On foot**
- **Your boots have worn clean through.** Wrap your feet in rags [75%] (safe) / Buy moccasins from a trader, 30 coins [90%] / Walk barefoot [35%] / Hitch a ride on a freight wagon [55%]
- **A freighter offers you a seat in exchange for a week of cooking.** Accept [70%] / Offer haircuts instead [65%] / Ride on the tailgate without asking [35%] / Decline politely [always] (safe)

**Donkey**
- **Your donkey has stopped and will not move.** Offer it carrots [95%] / Push it [35%] / Wait it out [85%] (safe) / Sing to it [50%]
- **Your donkey ate a missionary's hat.** Apologize [90%] (safe) / Pay for the hat, 30 coins [always] / Blame another donkey [40%] / Offer him a haircut to make up for it [70%]
- **A rancher's daughter offers to trade you a horse for your donkey.** Trade, plus 900 coins [85%] / Haggle, 600 coins [40%] / Offer a season of haircuts [35%] / Keep the donkey [always] (safe)

**Horse**
- **Your horse is limping.** Stop and check its hooves [90%] (safe) / Push on [45%] / Rest it a day [always] / Try a poultice [60%]
- **A tumbleweed spooks your horse.** Calm it down [70%] (safe) / Gallop past [40%] / Get down and lead it [90%] / Braid its mane to soothe it [55%]

**Wagon**
- **A terrible creaking from the wagon axle.** Stop and tighten it [85%] (safe) / Ignore it [40%] / Lighten the load, dump 2 provisions [75%] / Grease it with bacon fat [60%]
- **A deep, fast river. The wagon will have to cross.** Ford it [55%] / Caulk the wagon and float it [60%] / Pay the ferry, 50 each [95%] (safe) / Find a shallower crossing [70%]
- **A hill so steep the wagon could roll away.** Lower it down with ropes [85%] (safe) / Ride it down [30%] / Unload everything and carry it [90%] / Go around [80%]

**Parties only**
- **The party argues over which way to go.** Put it to a vote [60%] / Flip a coin [50%] / Let the oldest decide [70%] (safe) / Play the fiddle until everyone agrees [always]
- **Somebody forgot to pack the coffee.** Blame someone [40%] / Everyone chips in, 10 coins each [90%] (safe) / Brew chicory root [55%] / Go without [always]
- **Somebody ate the last of the bacon.** Investigate [50%] / Let it go [90%] (safe) / Accuse the dog [60%] / Ration everything from now on [70%]
- **Everyone trims each other's hair by the campfire.** Everyone trims everyone [60%] / Just a little off the ends [90%] (safe) / Matching hairstyles [45%] / Sell the clippings to a wigmaker [always]

## Build plan once this is agreed

- Backend: new tables (`trail_games`, `trail_players`, `trail_parties`, `trail_ledger`, `trail_events`,
  `trail_choices` for pending choices including thefts and meetings),
  `TrailService` hooked into the same 8/12/4/8 schedule, `TrailOdds` for tuning, `TrailController`
  under `/api/trail`, tests on the H2 test profile like `ContestTests`.
- Frontend: a Trail tab in `SiteShell` (players and managers), game page with map, your party panel,
  shop, pending choice, event log and purchase log; managers create games like contests.
