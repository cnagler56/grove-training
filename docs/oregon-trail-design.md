# The Grove Trail: design note (draft for review)

An Oregon Trail parody. Stylists earn coins from their Salon Ultimate service sales and spend them on food,
animals and wagons to get from the salon in Maple Grove, MN to Haystack Rock on the Oregon Coast.
This lives only on the `oregon-trail` branch.

## Basics

- A manager creates a trail game the way they create a contest: name, start date, length (30 to 50 turns,
  default 40), prize text, optional handicap, players. Receptionists and customers can't play.
- **One turn per Salon Ultimate check** (8 am, 12, 4 pm, 8 pm), so a 40 turn game runs 10 days. Coins arrive
  during the day, and every check is a little episode people can react to.
- **Coins:** $1 of service sales = 1 coin, plus 25 per treatment, 20 per color, 15 per wax (bonuses set when
  the game is created). Everyone also starts with 200 coins, so turn one isn't just standing in the parking lot.
- **Handicap (optional):** the contest handicaps reused. NONE, HOURS (fewer hours earns more per dollar),
  RATE (lower usual sales per hour earns more), BOTH. Coins are multiplied by worth / 100 (100 to 200),
  from the 4 weeks before the start.
- **Winning:** first party to reach the coast wins and splits the prize. When the last turn runs, everyone
  else is ranked by miles. People who arrive early wait on the beach and show as "At the coast".

## Each turn, in order

1. Credit new coins from Salon Ultimate (if the check fails, the turn still runs and coins catch up next time).
2. Settle last turn's scenario: roll the chosen option's odds and apply the good or bad outcome.
   Anyone in the party can answer; first answer counts. No answer by the check = the option marked default.
3. Everyone eats 1 snack pack (2 on Grueling pace). Out of food = Hangry: half speed until you buy food.
4. Move: speed of the party's travel mode x pace, minus any delays.
5. Deal a new scenario to every solo player and party (every turn, always 2 to 4 options).
   Nobody gets the same scenario twice until they've seen them all.
6. Landmark reached: a line in the party's log. The first party to reach each landmark, and everyone who
   reaches the coast, is posted on the Team Board. Only the coast gets a push notification (there's a
   landmark every couple of turns, so pushing each one would be noisy).

## Map and landmarks (1,870 trail miles)

A horizontal route line with landmark labels; every player or party is a labeled dot on the line, with the
name and miles beside it. Plain text, no icons.

| Miles | Landmark |
|---|---|
| 0 | Maple Grove, MN (the salon) |
| 65 | St. Cloud, MN |
| 135 | Big Ole the Viking, Alexandria |
| 235 | Fargo, ND |
| 330 | The World's Largest Buffalo, Jamestown |
| 440 | Salem Sue, the World's Largest Holstein |
| 490 | The Enchanted Highway |
| 560 | Medora, ND |
| 700 | Miles City, MT |
| 815 | Pompeys Pillar |
| 845 | Billings, MT |
| 990 | Bozeman, MT |
| 1060 | The Continental Divide |
| 1180 | Missoula, MT |
| 1280 | Lookout Pass, ID |
| 1350 | Coeur d'Alene, ID |
| 1385 | Spokane, WA |
| 1640 | The Columbia River Gorge |
| 1760 | Multnomah Falls |
| 1790 | Portland, OR |
| 1870 | Haystack Rock, Cannon Beach (the coast) |

## Travel modes

Speeds are miles per turn for a 40 turn game and scale with length (a 30 turn game moves 40/30 as fast),
so the same mode always takes the same share of the game.

| Mode | Cost | Speed | Turns to coast | Its own ridiculous risk |
|---|---|---|---|---|
| Walking | free | 28 | about 67 (won't make it) | Blisters (slower next turn); free rides from truckers |
| Donkey | 800 | 40 | about 47 | Stubborn: 1 in 8 turns refuses to move unless you have carrots (60); goes viral on TikTok |
| Horse | 2,000 | 52 | about 36 | Throws a shoe (walking speed until you buy horseshoes, 150); spooked by plastic bags |
| Wagon (with oxen, seats 4) | 6,000 | 64 | about 29 | Breaks down (2% a turn on its own, plus risky scenario options; doubled on Grueling): stuck until repaired for 1,200, or instantly with a spare wheel (400) |

Pace, chosen by the party: **Leisurely** (x0.8, bad events less likely, salon self care), **Steady** (x1),
**Grueling** (x1.25, eats double, bad events and breakdowns more likely).

Anyone in a party not riding in the wagon needs their own animal; without a wagon the party moves at its
slowest member's pace. The wagon can be abandoned (no refund) if nobody can pay to fix it.

Shop: snack packs (12 each), donkey, horse, wagon, wagon repair, spare wheel, horseshoes, carrots,
and "A Really Good Playlist" (100, cancels one party drama event).

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

- Invite someone within 60 miles of you. If they accept, the party sets off from the **rearmost member's
  spot** (the one in front "goes back for them"). Up to 4 people.
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
- **Joining:** only someone traveling alone can be invited; two parties don't merge. If both had wagons,
  the newcomer sells theirs back for half price.

## Stealing and helping

All the numbers below live in `TrailOdds` with the rubber-band ones.

**Karma.** Every theft gives the thief -5 points on their good-outcome chance for the next 4 turns.
Every act of help gives the helper +5 for 4 turns. Karma stacks up to plus or minus 15, and is still kept
inside the rubber-band limits (20 points either side of the base, 10% to 90%).

### Stealing from your own party

- **How often:** at the start of each turn, each member of a party of 2 or more has a 6% chance of being
  offered "Skim off the top." Nobody gets the offer in the first 3 turns, more than once every 5 turns, or
  in their first 2 turns after joining. In a 3 person party over 40 turns that works out to roughly 6
  offers in total. It's a private choice: only the thief sees it, and ignoring it means no.
- **What can be taken (pick one):** coins (up to 15% of the party's pooled coins, at most 300, taken from
  the other members evenly), up to 3 snack packs, or the spare wheel or horseshoes. Food and parts are
  sold on the spot ("to a trucker") for their shop price in coins. Never the wagon or an animal: they
  don't fit in a pocket.
- **Does anyone find out:** the party log always gets a line like "Someone took 120 coins from the
  party. The Glazers are also missing." and every other member gets a push notification. 40% of the time
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

- **Who meets:** two solo players or parties that end a turn within 10 miles of each other (or at the
  same landmark), not counting the starting line; each party meets at most one other party a turn. Each
  side gets the same choice for that turn: **Help**, **Steal**, or **Wave and keep
  going** (the default). The same two can't meet again for 6 turns. A party answers as one; the first
  member to answer decides, as with other choices.
- **Help (pick one):** give up to 5 snack packs, give up to 300 coins, pay to fix their broken-down
  wagon (you know a guy: 75% of the repair price), or give your spare wheel. Helping earns a
  **Good Samaritan** label on the map for 6 turns plus the karma bonus.
- **Steal (pick one):** up to 20% of their coins (at most 250), up to 3 snack packs, or their spare
  wheel or horseshoes. Never animals or wagons.
- **Chance a theft works**, so the rich can't snowball:
  - Worth = coins + the shop value of food and spare parts (animals and wagons don't count).
  - Success = 50% + 40 x (their worth - your worth) / (their worth + your worth), kept between 25% and 70%.
    Robbing someone twice as well off: about 63%. Robbing someone half as well off: about 37%.
  - If the thief is in the top third of the standings, the haul is halved ("you felt bad about it").
  - Stealing from someone with a broken-down wagon is 10 points less likely to work. Everyone is looking.
- **What happens when they cross:**
  - Steal works: the haul moves over, both get a log line, and the victim gets a notification naming the
    thief.
  - Steal fails: the thief is caught, gets Sticky Fingers, pays the target half of what they tried to take,
    and loses half a turn explaining themselves to a Montana sheriff.
  - One helps while the other steals: the theft gets +15% ("you held the door for them"), and the help
    still goes through. The log is merciless.
  - Both steal: "You both reached for each other's Glazers at the same time." Nothing changes hands,
    both get Sticky Fingers, and the two of you are banned from meeting again for the rest of the game.
  - Both help: each gets the Good Samaritan label, and the next event for both is good.

## Scenarios (53)

One scenario is dealt every turn. Each option is written as: **Option** (base chance it goes well):
good outcome / bad outcome. The default is what happens when nobody answers. "Lose a turn" means no
miles that turn. P = parties only. Stealing and meeting choices are on top of these (see above).

**On the road**
1. A crystal clear river. **Drink from it** (60%): energized and refreshed, +30 miles / polluted, you're
   sick, lose a turn. **Drink the emergency LaCroix** (85%, default): fine, +5 miles / it's pamplemousse,
   -10 miles of complaining. **Boil it first** (95%): safe, lose a quarter turn / you forgot the pot, lose half a turn.
2. A gas station sushi display. **Eat it** (35%): surprisingly great, +3 snack packs / you have died of
   dysentery. Just kidding. Lose a turn. **Buy a Glazer instead** (90%, default): +1 snack pack for 10 coins / sold out.
3. A goose blocks the trail and will not be negotiated with. **Negotiate anyway** (40%): it respects you,
   +20 miles / it does not, -30 miles. **Go around** (85%, default): -10 miles / -20 miles. **Offer it a snack pack** (70%): it leaves, -1 snack pack / it eats it and stays, -1 snack pack, -20 miles.
4. A frontage road Google Maps swears exists. **Take it** (55%): shortcut, +40 miles / it doesn't exist, -30 miles. **Stay on the interstate** (90%, default): nothing / construction, -10 miles.
5. Road construction, as in every season. **Follow the detour signs** (75%, default): -10 miles / the detour has a detour, -30 miles. **Drive through the cones** (40%): +20 miles / fined 150 coins.
6. Did you leave the flat iron on? **Go back and check** (95%): it was off, -30 miles / it was off and you checked twice, -50 miles. **Trust yourself** (60%, default): peace of mind, +10 miles / you think about it all turn, -20 miles.
7. A wrong turn. **Keep going, it'll work out** (40%): a scenic shortcut, +30 miles / you're in Canada now, they're very nice about it, -40 miles. **Turn around now** (90%, default): -10 miles / -20 miles.
8. You drop your phone near the Missouri River. **Fish it out** (50%): saved, and you find 50 coins in the mud / it's gone, navigate by vibes, -25 miles. **Let it go** (90%, default): -15 miles / -25 miles.
9. A tailwind across North Dakota, with literally nothing to stop it. **Ride it, full send** (65%): +50 miles / you overshoot a gas stop, -1 snack pack. **Keep it steady** (90%, default): +20 miles / +5 miles.
10. A Kwik Trip appears like a mirage. **Stock up** (80%): +4 snack packs for 30 coins / the Glazer case is empty, +1 for 30 coins. **Just bathroom** (95%, default): nothing / the line, -10 miles.
11. A church basement hot dish potluck. **Crash it** (65%): +5 snack packs / you're asked to lead the prayer, lose half a turn. **Bring bars** (85%, -1 snack pack): +6 snack packs / your bars are judged, +2. **Keep driving** (default): nothing.
12. A meat raffle at a VFW. **Buy tickets, 40 coins** (40%): +6 snack packs / nothing. **Watch** (default): nothing, but the vibes, +5 miles.
13. Snow in May. **Push through** (45%): +10 miles / stuck in a ditch, lose a turn. **Wait it out at a Perkins** (85%, default): lose half a turn, +1 snack pack / lose a turn.
14. Mosquitoes, the state bird of Minnesota. **Run** (60%): +20 miles / they carry off 2 snack packs. **Bug spray, 20 coins** (90%, default): fine / it's the off-brand kind, -1 snack pack.
15. Raccoons circling the cooler. **Guard it all night** (70%): saved, but -10 miles tired / they win anyway, -3 snack packs. **Bungee it shut** (60%, default): saved / they know bungees, -3 snack packs.
16. A buffalo herd crossing the road. **Wait** (90%, default): lose a quarter turn / lose half a turn. **Inch through** (50%): +10 miles / one sits on your bags, -2 snack packs and -20 miles.
17. Hail warning in Montana. **Shelter in a Culver's** (90%, default): lose half a turn, +2 snack packs / lose a turn, +2. **Push on** (45%): +30 miles / hail; wagons break down, everyone else loses a turn.
18. Mysterious fog. **Push through** (50%): +40 miles / lost, -40 miles. **Make camp** (90%, default): lose half a turn / lose a turn.
19. Montana hot springs. **Soak** (80%): Hangry cured, +10 good-luck points next scenario / you fall asleep, lose a turn. **Skip** (default): nothing.
20. Wild huckleberries in Idaho. **Pick a bunch** (70%): +4 snack packs / those were not huckleberries, lose half a turn. **Buy the jam, 30 coins** (95%, default): +2 snack packs / it's mostly sugar, +1.
21. Drive through the night? **Yes** (50%): +50 miles / you nod off at a rest stop until noon, lose half a turn and -1 snack pack. **No** (default): nothing.
22. Scenic route or interstate. **Scenic** (75%): -20 miles, next scenario +15 good-luck points / you get "lost in the moment," -40 miles. **Interstate** (90%, default): nothing / -10 miles.
23. A ghost town. **Explore** (50%): find a spare wheel or horseshoes / a "ghost" raccoon takes 2 snack packs. **Skip** (default): nothing.
24. An elk in the road. **Wait it out** (90%, default): lose a quarter turn / lose half a turn. **Honk** (45%): it leaves, +10 miles / it charges: wagon breaks down, or lose a turn.

**Salon life on the trail**
25. A stranger at a rest stop asks for a free haircut. **Do it** (50%): he's a rich rancher, +200 coins / he's just a guy, lose half a turn. **Recommend the salon** (85%, default): he books, +30 coins / he leaves a one star review for the salon, nothing.
26. A client texts "small emergency." **FaceTime them** (60%): fixed, they tip 100 coins / it's self cut bangs, lose half a turn. **Reply "be strong"** (80%, default): fine / they post about it, -20 miles of guilt.
27. A wedding party in Bozeman needs six updos. **Stay and do them** (65%): lose a turn, +300 coins / lose a turn, the bride cries, +100 coins. **Keep going** (default): nothing.
28. A rancher's wife needs a root touch up. **Do it in the barn** (70%): +150 coins / the lighting, +50 coins and lose half a turn. **Give her your card** (90%, default): +20 coins / nothing.
29. A snake oil salesman offers Miracle Bond Repair Tonic for 150. **Buy it** (40%): double miles next turn / it's just conditioner. **Pass** (default): nothing.
30. A rival salon's influencer challenges you to a blowout-off. **Accept** (50%): +200 coins / -100 coins and your dignity. **Walk away** (90%, default): nothing / they post you walking away, -10 miles.
31. A hitchhiker says he's a famous colorist. **Give him a ride** (50%): trade secrets, +100 coins / he steals 3 snack packs. **No thanks** (default): nothing.
32. The sheriff wants his mustache trimmed. **Do it** (70%): +50 coins and a police escort, +20 miles / you take a little too much, -50 coins. **Decline** (60%, default): he nods / he's offended, -20 miles.
33. Free Wi-Fi at a rest stop. **Rebook clients** (75%): +80 coins / the Wi-Fi is fake, lose a quarter turn. **Doom scroll** (default, 90%): +5 miles of serenity / -10 miles.
34. A five star review comes in naming you. **Screenshot it for the group chat** (90%, default): +20 miles / nobody reacts, +5. **Post it publicly** (60%): +50 miles of pure morale / a stranger replies "ok", nothing.
35. The lutefisk challenge at a Sons of Norway hall. **Eat it** (45%): +4 snack packs and respect / lose a turn. **Politely decline** (90%, default): Minnesota nice, nothing / they ask again, lose a quarter turn.
36. Slot machines at a Montana gas station. **Bet 100 coins** (45%): +100 coins / -100 coins. **Bet 10** (45%): +10 / -10. **Keep walking** (default): nothing.
37. A lost puppy. **Adopt** (80%): +10 good-luck points for the rest of the game, eats 1 snack pack a turn / it runs away with a snack pack. **Drop it at a shelter** (default, 95%): lose a quarter turn / lose half a turn.

**Travel mode scenarios** (dealt only to the right mode)
38. Walking: blisters. **Walk it off** (50%): fine / -40% miles next turn. **Moleskin, 20 coins** (90%, default): fine / you're out, -20 miles.
39. Walking: a trucker at a Love's offers a ride. **Hop in** (70%): +60 miles / he only goes to the next exit, +5. **Walk** (default): nothing.
40. Donkey: your donkey is having a moment. **Carrots** (95% if you have them): it moves, -1 carrots / no carrots, lose a turn. **Reason with it** (35%, default): it moves / lose a turn.
41. Donkey: your donkey goes viral on TikTok. **Lean in** (55%): fans tip 150 coins / fans swarm, lose a turn. **Hide** (85%, default): lose a quarter turn / lose half a turn.
42. Donkey: a horse girl at a ranch offers to trade you a horse for your donkey plus 900 coins. **Trade** (85%): you have a horse / it's a very old horse, donkey speed. **Keep the donkey** (default): it seems grateful, +10 miles.
43. Horse: your horse is limping. **Stop and check** (90%, default): a pebble, lose a quarter turn / a loose shoe, walking speed until horseshoes. **Push on** (45%): +10 miles / thrown shoe, walking speed until horseshoes.
44. Horse: a plastic bag on the wind. **Calm it down** (70%, default): fine / spooked, lose a turn. **Gallop past** (40%): +30 miles / spooked into Wyoming, lose a turn and -20 miles.
45. Wagon: a strange noise from the axle. **Stop and tighten it** (85%, default): lose a quarter turn / it breaks anyway. **Turn up the music** (40%): it goes away / the axle snaps, wagon breaks down.
46. Wagon: a river crossing. **Ford it** (55%): free / lose 3 snack packs. **Caulk and float** (60%): +20 miles / wagon breaks down. **Pay the ferry, 50 each** (95%, default): safe / the ferry is late, lose half a turn.

**Party drama** (P)
47. Aux cord war: Taylor Swift or a true crime podcast. **Taylor** (50%) or **Podcast** (50%): the party bonds, +20 miles / someone sulks, -15% miles. **Play the Really Good Playlist** (100% if you have it, default otherwise Taylor).
48. "Who packed the cooler?" **Blame someone** (40%): they pack it next time, +2 snack packs / drama, -2 snack packs and -20 miles. **Everyone chips in, 10 coins each** (90%, default): +3 snack packs / +1.

49. The pop versus soda debate. **Say pop** (60%): the Minnesotans approve, +10 miles / it escalates, -20 miles. **Say soda** (40%): you win the argument, +20 miles / nobody speaks for 40 miles, -30. **Say "Coke"** (80%, default): everyone is confused into silence, nothing / -10 miles.
50. Somebody's ex texts the group chat. **Read it out loud** (45%): it's an apology and a 100 coin Venmo / lose half a turn processing. **Leave them on read** (85%, default): +10 miles of power / -10 miles of wondering.
51. Synchronized bangs at a rest stop. **Everyone trims everyone** (60%): you look amazing, +30 miles / one bang is crooked and nobody will say whose, -20 miles. **Just touch ups** (90%, default): +10 miles / -5 miles.
52. A detour to the World's Largest Holstein gift shop. **Go** (75%): -30 miles, next scenario +15 good-luck points / you buy a 60 coin cow hat each. **Skip it** (90%, default): nothing / someone brings it up for 200 miles, -10.
53. Somebody ate the last bar. **Investigate** (50%): the culprit confesses and buys 3 snack packs / everyone is a suspect, -1 snack pack and -20 miles. **Let it go** (90%, default): -1 snack pack / -1 snack pack and a lot of feelings.

## Build plan once this is agreed

- Backend: new tables (`trail_games`, `trail_players`, `trail_parties`, `trail_ledger`, `trail_events`,
  `trail_choices` for pending choices including thefts and meetings),
  `TrailService` hooked into the same 8/12/4/8 schedule, `TrailOdds` for tuning, `TrailController`
  under `/api/trail`, tests on the H2 test profile like `ContestTests`.
- Frontend: a Trail tab in `SiteShell` (players and managers), game page with map, your party panel,
  shop, pending choice, event log and purchase log; managers create games like contests.
