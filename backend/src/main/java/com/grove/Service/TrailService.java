package com.grove.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Contest.Handicap;
import com.grove.Domain.Role;
import com.grove.Domain.TrailChoice;
import com.grove.Domain.TrailGame;
import com.grove.Domain.TrailLog;
import com.grove.Domain.TrailParty;
import com.grove.Domain.TrailParty.Pace;
import com.grove.Domain.TrailPlayer;
import com.grove.Domain.TrailPlayer.Mount;
import com.grove.Domain.User;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.TrailChoiceRepository;
import com.grove.Repository.TrailGameRepository;
import com.grove.Repository.TrailLogRepository;
import com.grove.Repository.TrailPartyRepository;
import com.grove.Repository.TrailPlayerRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Service.TrailOdds.Item;
import com.grove.Service.TrailScenarios.Fx;
import com.grove.Service.TrailScenarios.Needs;
import com.grove.Service.TrailScenarios.Option;
import com.grove.Service.TrailScenarios.Scenario;
import com.grove.Service.TrailScenarios.Who;

/**
 * The Grove Trail: stylists race from Maple Grove to the Oregon Coast, buying food,
 * animals, and wagons with coins from their Salon Ultimate service sales. A turn runs
 * at every Salon Ultimate check; each turn every party answers a multiple choice
 * scenario whose odds quietly favor whoever is behind. People can travel alone or
 * together, steal from each other, or help. Managers and up start games.
 */
@Service
public class TrailService {

	private static final Logger log = LoggerFactory.getLogger(TrailService.class);
	private static final String PAGE = "/trail";
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");

	public record GameInput(String name, LocalDate startDate, Integer turns, String prize, Handicap handicap,
		List<Long> userIds, Integer bonusTreatment, Integer bonusColor, Integer bonusWax) {}

	public record BuyInput(Item item, Integer quantity, Boolean split) {}

	public record LandmarkView(String name, int miles) {}

	public record MemberView(Long userId, String name, String mount, List<String> labels) {}

	/** {@code place} is null before the first turn. */
	public record PartyView(Long id, int miles, String at, Integer arrivedTurn, Integer place, String travel,
		boolean wagonBroken, List<MemberView> members) {}

	public record MyMember(Long userId, String name, int coins, String mount, boolean shoeThrown) {}

	public record MyParty(Long id, int food, String pace, boolean hangry, boolean wagon, boolean wagonBroken,
		int spareWheels, int horseshoes, int carrots, int playlists, boolean puppy, List<MyMember> members) {}

	/** {@code bad} is null for options that always go one way. */
	public record OptionView(int index, String label, String good, String bad, boolean isDefault, boolean available) {}

	public record ScenarioView(String key, String text, List<OptionView> options, Integer chosen, String chosenByName) {}

	public record ChoiceOption(String value, String label) {}

	public record ChoiceView(Long id, String kind, String title, String text, List<ChoiceOption> options, String answer,
		String answeredByName, int expiresTurn) {}

	public record InviteTarget(Long userId, String name, int miles) {}

	public record LogView(int turn, String kind, String body, Integer coins) {}

	public record MeView(Long userId, int coins, String mount, boolean shoeThrown, int noJoinUntil, MyParty party,
		ScenarioView scenario, List<ChoiceView> choices, List<InviteTarget> canInvite, List<LogView> purchases) {}

	public record ShopItem(String item, String label, int price, String help) {}

	/** {@code status}: UPCOMING, LIVE, or FINISHED. */
	public record GameView(Long id, String name, String status, int turn, int turns, LocalDate startDate, String prize,
		String handicap, int bonusTreatment, int bonusColor, int bonusWax, LocalDateTime lastCheckedAt, String checkProblem,
		String createdByName, int trailMiles, List<LandmarkView> landmarks, List<PartyView> parties, MeView me,
		List<ShopItem> shop, List<LogView> log) {}

	public record Eligible(Long userId, String name) {}

	private final TrailGameRepository games;
	private final TrailPartyRepository parties;
	private final TrailPlayerRepository players;
	private final TrailChoiceRepository choices;
	private final TrailLogRepository logs;
	private final UserRepository users;
	private final WeeklyStatRepository stats;
	private final SalonUltimateClient salonUltimate;
	private final PushService push;
	private final TeamBoardService teamBoard;
	private final ZoneId zone;
	private Random random = new Random();

	public TrailService(TrailGameRepository games, TrailPartyRepository parties, TrailPlayerRepository players,
			TrailChoiceRepository choices, TrailLogRepository logs, UserRepository users, WeeklyStatRepository stats,
			SalonUltimateClient salonUltimate, PushService push, TeamBoardService teamBoard,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.games = games;
		this.parties = parties;
		this.players = players;
		this.choices = choices;
		this.logs = logs;
		this.users = users;
		this.stats = stats;
		this.salonUltimate = salonUltimate;
		this.push = push;
		this.teamBoard = teamBoard;
		this.zone = ZoneId.of(zone);
	}

	/** For tests: dice that roll the same way every time. */
	void setRandom(Random random) {
		this.random = random;
	}

	// Games.

	/** Games still going first, newest first, then the last few finished ones. */
	public List<GameView> list(User viewer) {
		requireStaff(viewer);
		List<TrailGame> all = games.findAll();
		List<TrailGame> shown = new ArrayList<>();
		all.stream().filter(g -> g.getFinishedAt() == null)
			.sorted(Comparator.comparing(TrailGame::getStartDate).reversed()).forEach(shown::add);
		all.stream().filter(g -> g.getFinishedAt() != null)
			.sorted(Comparator.comparing(TrailGame::getFinishedAt).reversed()).limit(5).forEach(shown::add);
		return shown.stream().map(g -> view(g, viewer)).toList();
	}

	public GameView get(User viewer, Long id) {
		requireStaff(viewer);
		return view(find(id), viewer);
	}

	public List<Eligible> eligible(User manager) {
		requireManager(manager);
		return users.findAll().stream().filter(this::canPlay)
			.map(u -> new Eligible(u.getUserId(), name(u)))
			.sorted(Comparator.comparing((Eligible e) -> e.name().toLowerCase()))
			.toList();
	}

	@Transactional
	public GameView create(User manager, GameInput input) {
		requireManager(manager);
		if (input == null) throw bad("Fill in the game");
		String name = trim(input.name());
		if (name == null) throw bad("Give the game a name");
		if (name.length() > 80) throw bad("Keep the name to 80 characters");
		LocalDate start = input.startDate() == null ? today() : input.startDate();
		if (start.isBefore(today())) throw bad("The first day can't be in the past");
		int turns = input.turns() == null ? TrailOdds.DEFAULT_TURNS : input.turns();
		if (turns < TrailOdds.MIN_TURNS || turns > TrailOdds.MAX_TURNS) {
			throw bad("A game lasts " + TrailOdds.MIN_TURNS + " to " + TrailOdds.MAX_TURNS + " turns");
		}
		String prize = trim(input.prize());
		if (prize != null && prize.length() > 200) throw bad("Keep the prize to 200 characters");
		int treatment = bonus(input.bonusTreatment(), TrailOdds.DEFAULT_BONUS_TREATMENT);
		int color = bonus(input.bonusColor(), TrailOdds.DEFAULT_BONUS_COLOR);
		int wax = bonus(input.bonusWax(), TrailOdds.DEFAULT_BONUS_WAX);

		List<User> group = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Long id : input.userIds() == null ? List.<Long>of() : input.userIds()) {
			if (id == null || !seen.add(id)) continue;
			User u = users.findById(id).orElse(null);
			if (u == null || !u.isActive() || !u.getRoles().isStaff()) throw bad("One of those people isn't on the team anymore");
			if (u.getRoles() == Role.RECEPTIONIST) throw bad("Receptionists don't play the Grove Trail");
			if (!canPlay(u)) throw bad(name(u) + " isn't linked to Salon Ultimate yet. Link them on the Team page first.");
			group.add(u);
		}
		if (group.size() < 2) throw bad("Pick at least two people");

		Handicap handicap = input.handicap() == null ? Handicap.NONE : input.handicap();
		TrailGame g = new TrailGame();
		g.setName(name);
		g.setStartDate(start);
		g.setTurns(turns);
		g.setPrize(prize);
		g.setHandicap(handicap);
		g.setBonusTreatment(treatment);
		g.setBonusColor(color);
		g.setBonusWax(wax);
		g.setPriceScale(priceScale(group, start));
		g.setCreatedBy(manager.getUserId());
		g.setCreatedAt(LocalDateTime.now(zone));
		g = games.save(g);

		Map<Long, Integer> worths = handicap == Handicap.NONE ? Map.of() : worths(handicap, start, group);
		for (User u : group) {
			TrailParty p = new TrailParty();
			p.setGameId(g.getId());
			p.setFood(TrailOdds.STARTING_FOOD);
			p = parties.save(p);
			TrailPlayer pl = new TrailPlayer();
			pl.setGameId(g.getId());
			pl.setUserId(u.getUserId());
			pl.setPartyId(p.getId());
			pl.setCoins(TrailOdds.STARTING_COINS);
			pl.setWorth(worths.getOrDefault(u.getUserId(), ContestService.BASE_WORTH));
			players.save(pl);
		}
		push.notify(group.stream().map(User::getUserId).toList(), name,
			"You're on the Grove Trail, starting " + (start.equals(today()) ? "today" : start.format(SHORT_DATE))
				+ ". Pack snacks. Maybe find a wagon buddy.", PAGE);
		return view(g, manager);
	}

	@Transactional
	public void delete(User manager, Long id) {
		requireManager(manager);
		TrailGame g = find(id);
		logs.deleteByGameId(id);
		choices.deleteByGameId(id);
		players.deleteByGameId(id);
		parties.deleteByGameId(id);
		games.delete(g);
	}

	/** Play the next turn now instead of waiting for the next check. */
	public GameView runTurnNow(User manager, Long id) {
		requireManager(manager);
		TrailGame g = find(id);
		if (g.getFinishedAt() != null) throw bad("This game is already over");
		if (g.getStartDate().isAfter(today())) throw bad("This game hasn't started yet");
		turn(g);
		return view(find(id), manager);
	}

	/** One turn at every Salon Ultimate check, the same times as contests. */
	@Scheduled(cron = "0 0 8,12,16,20 * * *", zone = "${app.time-zone:America/Chicago}")
	public void turnAll() {
		for (TrailGame g : games.findByFinishedAtIsNullAndStartDateLessThanEqual(today())) {
			try {
				turn(g);
			} catch (Exception e) {
				log.warn("Trail turn for game {} failed", g.getId(), e);
			}
		}
	}

	// A turn.

	/** Everything that changes in one turn, kept together while it runs. */
	private final class Turn {
		final TrailGame game;
		final int t;
		final Map<Long, TrailParty> partyById = new LinkedHashMap<>();
		final List<TrailPlayer> everyone;
		final Map<Long, User> people;
		/** This turn's miles multiplier and flat miles, per party, from scenarios and meetings. */
		final Map<Long, Double> move = new HashMap<>();
		final Map<Long, Integer> extra = new HashMap<>();

		Turn(TrailGame game) {
			this.game = game;
			this.t = game.getTurn();
			parties.findByGameId(game.getId()).forEach(p -> partyById.put(p.getId(), p));
			everyone = players.findByGameId(game.getId());
			people = users.findAllById(everyone.stream().map(TrailPlayer::getUserId).toList()).stream()
				.collect(Collectors.toMap(User::getUserId, Function.identity()));
		}

		List<TrailPlayer> members(TrailParty p) {
			return everyone.stream().filter(pl -> pl.getPartyId().equals(p.getId())).toList();
		}

		List<TrailParty> onTrail() {
			return partyById.values().stream().filter(p -> p.getArrivedTurn() == null && !members(p).isEmpty()).toList();
		}

		TrailPlayer player(Long userId) {
			return everyone.stream().filter(pl -> pl.getUserId().equals(userId)).findFirst().orElse(null);
		}

		String nameOf(Long userId) {
			User u = userId == null ? null : people.get(userId);
			return u == null ? "Former employee" : name(u);
		}

		String names(TrailParty p) {
			return joinNames(members(p).stream().map(pl -> nameOf(pl.getUserId())).toList());
		}

		List<Long> ids(TrailParty p) {
			return members(p).stream().map(TrailPlayer::getUserId).toList();
		}

		int price(Item item) {
			return TrailOdds.price(item, game.getPriceScale());
		}

		/** How far behind the average the party is, as a share of the trail (negative when ahead). */
		double behind(TrailParty p) {
			double average = everyone.stream()
				.map(pl -> partyById.get(pl.getPartyId()))
				.filter(Objects::nonNull)
				.mapToInt(TrailParty::getMiles).average().orElse(p.getMiles());
			return (average - p.getMiles()) / TrailOdds.TRAIL_MILES;
		}

		/** Odds points from karma, pace, luck, and a puppy. */
		int bonus(TrailParty p) {
			List<TrailPlayer> m = members(p);
			int karma = (int) Math.round(m.stream().mapToInt(pl -> karmaNow(pl, t)).average().orElse(0));
			return karma + TrailOdds.paceLuck(p.getPace()) + p.getLuck() + (p.isPuppy() ? TrailOdds.PUPPY_LUCK : 0);
		}

		void story(TrailParty p, TrailLog.Kind kind, String body) {
			addLog(game, p == null ? null : p.getId(), null, t, kind, body, null);
		}

		void save() {
			parties.saveAll(partyById.values());
			players.saveAll(everyone);
			games.save(game);
		}
	}

	/** Play one turn of this game. One at a time. */
	synchronized void turn(TrailGame g) {
		if (g.getFinishedAt() != null) return;
		g.setTurn(g.getTurn() + 1);
		games.save(g);
		Turn turn = new Turn(g);

		String problem = creditCoins(turn);
		g.setCheckProblem(problem == null ? null : problem.length() <= 500 ? problem : problem.substring(0, 500));
		if (problem == null) g.setLastCheckedAt(LocalDateTime.now(zone));

		for (TrailParty p : turn.onTrail()) settleScenario(turn, p);
		settleMeetings(turn);
		expireChoices(turn);
		for (TrailParty p : turn.onTrail()) eat(turn, p);
		for (TrailParty p : turn.onTrail()) travel(turn, p);
		turn.save();

		boolean over = turn.t >= g.getTurns() || turn.onTrail().isEmpty();
		if (over) {
			finish(turn);
			return;
		}
		for (TrailParty p : turn.onTrail()) deal(turn, p);
		offerSkims(turn);
		arrangeMeetings(turn);
		turn.save();
	}

	/**
	 * Coins from Salon Ultimate since the game started: $1 of service sales is 1 coin, plus a
	 * bonus for each treatment, color, and wax, times each person's handicap. Only what's new
	 * since the last turn is added. Returns why it couldn't be read, or null.
	 */
	private String creditCoins(Turn turn) {
		if (!salonUltimate.isConfigured()) {
			return "Salon Ultimate sign-in isn't set up on the server (SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD).";
		}
		TrailGame g = turn.game;
		LocalDate through = today();
		try {
			String pay = salonUltimate.weekReport(g.getStartDate(), through);
			List<SalonUltimateReport.Row> rows = pay == null ? List.of() : SalonUltimateReport.parse(pay);
			if (rows.isEmpty() && pay != null) {
				String blocked = SalonUltimateReport.blockedBy(pay);
				if (blocked != null) return "Salon Ultimate won't run the report until these timecards are closed: " + blocked;
			}
			String detail = salonUltimate.detailedServiceByEmployee(g.getStartDate(), through);
			Map<String, SalonUltimateReport.ServiceTypes> counts = detail == null ? Map.of() : SalonUltimateReport.parseDetailedService(detail);
			Map<String, BigDecimal> sales = new HashMap<>();
			for (SalonUltimateReport.Row r : rows) sales.put(r.name().toLowerCase(), r.netServiceSales());
			for (TrailPlayer pl : turn.everyone) {
				User u = turn.people.get(pl.getUserId());
				if (u == null || u.getSalonUltimateName() == null) continue;
				String su = u.getSalonUltimateName().toLowerCase();
				double raw = sales.getOrDefault(su, BigDecimal.ZERO).doubleValue();
				SalonUltimateReport.ServiceTypes types = counts.get(su);
				if (types != null) {
					raw += types.treatments() * g.getBonusTreatment() + types.colors() * g.getBonusColor() + types.waxes() * g.getBonusWax();
				}
				int total = (int) Math.floor(raw * pl.getWorth() / 100.0);
				if (total > pl.getEarned()) {
					pl.setCoins(pl.getCoins() + total - pl.getEarned());
					pl.setEarned(total);
				}
			}
			return null;
		} catch (SalonUltimateClient.LoginFailed e) {
			return "Salon Ultimate didn't accept the sign-in. Check SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD.";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "The check was interrupted. Coins catch up at the next turn.";
		} catch (Exception e) {
			log.warn("Salon Ultimate pull for trail game {} failed", g.getId(), e);
			return "Couldn't reach Salon Ultimate: " + e.getMessage();
		}
	}

	/** Roll last turn's scenario with the party's answer (or the default) and apply what happened. */
	private void settleScenario(Turn turn, TrailParty p) {
		Scenario sc = p.getScenarioKey() == null ? null : TrailScenarios.BY_KEY.get(p.getScenarioKey());
		Integer answer = p.getScenarioAnswer();
		Long answeredBy = p.getAnsweredBy();
		p.setScenarioKey(null);
		p.setScenarioAnswer(null);
		p.setAnsweredBy(null);
		if (sc == null) return;

		List<TrailPlayer> members = turn.members(p);
		int index = answer != null && answer >= 0 && answer < sc.options().size() && available(sc.options().get(answer), p, members)
			? answer : sc.defaultOption();
		Option op = sc.options().get(index);
		String who = answer != null && index == answer && answeredBy != null
			? turn.nameOf(answeredBy) + " picked: " + op.label + "."
			: "Nobody answered, so: " + op.label + ".";

		boolean good;
		String paid = "";
		if (op.cost > 0) {
			int cost = op.costEach ? op.cost * members.size() : op.cost;
			Map<TrailPlayer, Integer> taken = charge(members, cost);
			if (taken == null) {
				good = false;
				paid = " You couldn't afford it.";
			} else {
				logPurchases(turn.game, p, turn.t, taken, op.label);
				good = op.sure() || roll(TrailOdds.chance(op.base, turn.behind(p), turn.bonus(p)));
			}
		} else {
			good = op.sure() || roll(TrailOdds.chance(op.base, turn.behind(p), turn.bonus(p)));
		}
		p.setLuck(0);
		Fx fx = good ? op.good : op.bad;
		String effects = apply(turn, p, members, fx);
		turn.story(p, TrailLog.Kind.STORY, sc.text() + " " + who + paid + " " + fx.text + effects);
	}

	/** Apply an outcome to the party. Returns anything extra worth saying (a spare wheel used, say). */
	private String apply(Turn turn, TrailParty p, List<TrailPlayer> members, Fx fx) {
		StringBuilder more = new StringBuilder();
		turn.move.merge(p.getId(), fx.move, (a, b) -> a * b);
		if (fx.miles != 0) turn.extra.merge(p.getId(), fx.miles, Integer::sum);
		if (fx.food != 0) p.setFood(Math.max(0, p.getFood() + fx.food));
		if (fx.coins > 0) give(members, fx.coins);
		if (fx.coins < 0) take(members, -fx.coins);
		if (fx.luck != 0) p.setLuck(fx.luck);
		if (fx.wheels != 0) p.setSpareWheels(Math.max(0, p.getSpareWheels() + fx.wheels));
		if (fx.shoes != 0) p.setHorseshoes(Math.max(0, p.getHorseshoes() + fx.shoes));
		if (fx.carrots != 0) p.setCarrots(Math.max(0, p.getCarrots() + fx.carrots));
		if (fx.puppy) p.setPuppy(true);
		if (fx.cureHangry) p.setHangry(false);
		if (fx.horseForDonkey) members.stream().filter(pl -> pl.getMount() == Mount.DONKEY).forEach(pl -> pl.setMount(Mount.HORSE));
		if (fx.throwShoe && !p.isWagon()) {
			members.stream().filter(pl -> pl.getMount() == Mount.HORSE).forEach(pl -> pl.setShoeThrown(true));
			if (fixShoes(p, members)) more.append(" You had horseshoes, so the farrier fixed it on the spot.");
		}
		if (fx.breakWagon && p.isWagon()) more.append(breakDown(turn, p));
		return more.toString();
	}

	/** The wagon breaks; a spare wheel fixes it on the spot. */
	private String breakDown(Turn turn, TrailParty p) {
		if (p.isWagonBroken()) return "";
		if (p.getSpareWheels() > 0) {
			p.setSpareWheels(p.getSpareWheels() - 1);
			return " A spare wheel saved the day.";
		}
		p.setWagonBroken(true);
		push.notify(turn.ids(p), turn.game.getName(), "Your wagon broke down. It won't move until someone pays for a repair or a spare wheel.", PAGE);
		return " The wagon is broken down until it's repaired.";
	}

	/** Out of food: Hangry, at half speed, until there's food again. */
	private void eat(Turn turn, TrailParty p) {
		int need = turn.members(p).size() * TrailOdds.paceFood(p.getPace()) + (p.isPuppy() ? 1 : 0);
		if (p.getFood() >= need) {
			p.setFood(p.getFood() - need);
			p.setHangry(false);
			return;
		}
		p.setFood(0);
		if (!p.isHangry()) turn.story(p, TrailLog.Kind.STORY, "Out of snack packs. Everyone is Hangry: half speed until you buy food.");
		p.setHangry(true);
	}

	private void travel(Turn turn, TrailParty p) {
		List<TrailPlayer> members = turn.members(p);
		fixShoes(p, members);
		int before = p.getMiles();
		if (!p.isWagonBroken()) {
			int base = p.isWagon() ? TrailOdds.WAGON_SPEED
				: members.stream().mapToInt(pl -> TrailOdds.speed(pl.getMount(), pl.isShoeThrown())).min().orElse(TrailOdds.WALK_SPEED);
			double miles = base * TrailOdds.lengthScale(turn.game.getTurns()) * TrailOdds.paceSpeed(p.getPace())
				* (p.isHangry() ? TrailOdds.HANGRY_SPEED : 1) * turn.move.getOrDefault(p.getId(), 1.0)
				+ turn.extra.getOrDefault(p.getId(), 0);
			p.setMiles(Math.max(0, Math.min(TrailOdds.TRAIL_MILES, before + (int) Math.round(miles))));
		}
		if (p.isWagon() && !p.isWagonBroken() && p.getMiles() > before) {
			double chance = TrailOdds.WAGON_BREAKDOWN * (p.getPace() == Pace.GRUELING ? 2 : 1);
			if (random.nextDouble() < chance) turn.story(p, TrailLog.Kind.STORY, "A wheel falls off for no reason at all." + breakDown(turn, p));
		}

		int from = TrailOdds.landmarkAt(before);
		int to = TrailOdds.landmarkAt(p.getMiles());
		if (to <= from) return;
		String place = TrailOdds.LANDMARKS.get(to).name();
		boolean coast = p.getMiles() >= TrailOdds.TRAIL_MILES;
		if (coast) p.setArrivedTurn(turn.t);
		turn.story(p, TrailLog.Kind.LANDMARK, (coast ? "You made it to " : "You reached ") + place + ".");
		// Only the coast gets a notification of its own; there's a landmark every couple of turns.
		if (coast) {
			push.notify(turn.ids(p), turn.game.getName(),
				"You made it to the Oregon Coast on turn " + turn.t + ". Enjoy Haystack Rock while the others catch up.", PAGE);
		}
		if (coast || to > turn.game.getFurthestLandmark()) {
			String first = to > turn.game.getFurthestLandmark() ? "first" : "";
			turn.game.setFurthestLandmark(Math.max(turn.game.getFurthestLandmark(), to));
			String text = coast
				? turn.names(p) + " made it to the Oregon Coast" + (first.isEmpty() ? "" : ", first of everyone") + "!"
				: turn.names(p) + " reached " + place + " first.";
			turn.story(null, TrailLog.Kind.LANDMARK, text);
			announce(turn.game, turn.game.getName() + ": " + text + " (turn " + turn.t + " of " + turn.game.getTurns() + ")");
		}
	}

	/** Pick an unseen scenario this party can get. */
	private void deal(Turn turn, TrailParty p) {
		List<TrailPlayer> members = turn.members(p);
		Set<Who> fits = new HashSet<>();
		fits.add(Who.ANY);
		if (members.size() > 1) fits.add(Who.PARTY);
		if (p.isWagon()) fits.add(Who.WAGON);
		else members.forEach(pl -> fits.add(switch (pl.getMount()) {
			case WALK -> Who.WALK;
			case DONKEY -> Who.DONKEY;
			case HORSE -> Who.HORSE;
		}));
		Set<String> seen = new HashSet<>(p.getSeen() == null || p.getSeen().isBlank() ? List.of() : List.of(p.getSeen().split(",")));
		List<Scenario> options = TrailScenarios.ALL.stream().filter(s -> fits.contains(s.who()) && !seen.contains(s.key())).toList();
		if (options.isEmpty()) {
			seen.clear();
			options = TrailScenarios.ALL.stream().filter(s -> fits.contains(s.who())).toList();
		}
		Scenario sc = options.get(random.nextInt(options.size()));
		seen.add(sc.key());
		p.setScenarioKey(sc.key());
		p.setScenarioAnswer(null);
		p.setAnsweredBy(null);
		p.setSeen(String.join(",", seen));
	}

	private void finish(Turn turn) {
		TrailGame g = turn.game;
		g.setFinishedAt(LocalDateTime.now(zone));
		games.save(g);
		List<TrailParty> order = standings(turn.partyById.values().stream().filter(p -> !turn.members(p).isEmpty()).toList());
		Map<Long, Integer> places = places(order);
		List<String> lines = new ArrayList<>();
		lines.add(g.getName() + " is over after " + turn.t + " turns. Final standings:");
		for (TrailParty p : order) {
			String where = p.getArrivedTurn() != null ? "made it to the coast on turn " + p.getArrivedTurn()
				: "mile " + p.getMiles() + ", near " + TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(p.getMiles())).name();
			lines.add(ContestService.ordinal(places.get(p.getId())) + ": " + turn.names(p) + ", " + where);
		}
		List<TrailParty> winners = order.stream().filter(p -> places.get(p.getId()) == 1).toList();
		int winnerCount = winners.stream().mapToInt(p -> turn.members(p).size()).sum();
		if (g.getPrize() != null) {
			lines.add("");
			lines.add("Prize: " + g.getPrize() + (winnerCount > 1 ? ", split " + winnerCount + " ways." : "."));
		}
		turn.story(null, TrailLog.Kind.LANDMARK, String.join(" ", lines.subList(0, Math.min(lines.size(), 4))));
		announce(g, String.join("\n", lines));
		for (TrailParty p : order) {
			push.notify(turn.ids(p), g.getName() + " is over",
				"You finished " + ContestService.ordinal(places.get(p.getId())) + (p.getArrivedTurn() != null ? ", at the coast." : ", at mile " + p.getMiles() + "."),
				PAGE);
		}
	}

	// Stealing and helping.

	/** Now and then, someone in a party gets a private chance to skim. */
	private void offerSkims(Turn turn) {
		for (TrailParty p : turn.onTrail()) {
			List<TrailPlayer> members = turn.members(p);
			if (members.size() < 2) continue;
			for (TrailPlayer pl : members) {
				if (turn.t < TrailOdds.SKIM_FIRST_TURN || turn.t - pl.getLastSkimTurn() < TrailOdds.SKIM_EVERY
					|| turn.t - pl.getJoinedTurn() < TrailOdds.SKIM_AFTER_JOINING) continue;
				if (random.nextDouble() >= TrailOdds.SKIM_OFFER) continue;
				pl.setLastSkimTurn(turn.t);
				TrailChoice c = choice(turn.game, TrailChoice.Kind.SKIM, turn.t, turn.t);
				c.setUserId(pl.getUserId());
				c.setPartyId(p.getId());
				choices.save(c);
				push.notify(List.of(pl.getUserId()), turn.game.getName(), "Psst. Nobody is watching the party's coins right now.", PAGE);
			}
		}
	}

	/** Parties that end a turn close together each get to help, steal, or wave. One meeting per party a turn. */
	private void arrangeMeetings(Turn turn) {
		List<TrailChoice> past = choices.findByGameId(turn.game.getId()).stream()
			.filter(c -> c.getKind() == TrailChoice.Kind.MEET).toList();
		List<TrailParty> open = new ArrayList<>(turn.onTrail().stream().filter(p -> p.getMiles() > 0).toList());
		Set<Long> busy = new HashSet<>();
		for (TrailParty a : open) {
			if (busy.contains(a.getId())) continue;
			TrailParty b = open.stream()
				.filter(o -> !o.getId().equals(a.getId()) && !busy.contains(o.getId()))
				.filter(o -> Math.abs(o.getMiles() - a.getMiles()) <= TrailOdds.MEET_MILES)
				.filter(o -> past.stream().noneMatch(c -> c.getPartyId().equals(a.getId()) && c.getOtherPartyId().equals(o.getId())
					&& (c.getAmount() < 0 || turn.t - c.getOfferedTurn() < TrailOdds.MEET_AGAIN_TURNS)))
				.min(Comparator.comparingInt(o -> Math.abs(o.getMiles() - a.getMiles())))
				.orElse(null);
			if (b == null) continue;
			busy.add(a.getId());
			busy.add(b.getId());
			String where = TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(a.getMiles())).name();
			for (TrailParty[] side : new TrailParty[][] { { a, b }, { b, a } }) {
				TrailChoice c = choice(turn.game, TrailChoice.Kind.MEET, turn.t, turn.t);
				c.setPartyId(side[0].getId());
				c.setOtherPartyId(side[1].getId());
				choices.save(c);
				push.notify(turn.ids(side[0]), turn.game.getName(),
					"You ran into " + turn.names(side[1]) + " past " + where + ". Help them, rob them, or wave?", PAGE);
			}
		}
	}

	/** Meetings from last turn, both sides at once. */
	private void settleMeetings(Turn turn) {
		List<TrailChoice> open = choices.findByGameIdAndResolvedFalse(turn.game.getId()).stream()
			.filter(c -> c.getKind() == TrailChoice.Kind.MEET && c.getExpiresTurn() < turn.t).toList();
		Set<Long> done = new HashSet<>();
		for (TrailChoice a : open) {
			if (done.contains(a.getId())) continue;
			TrailChoice b = open.stream().filter(o -> o.getPartyId().equals(a.getOtherPartyId())
				&& o.getOtherPartyId().equals(a.getPartyId()) && o.getOfferedTurn() == a.getOfferedTurn()).findFirst().orElse(null);
			done.add(a.getId());
			a.setResolved(true);
			if (b != null) {
				done.add(b.getId());
				b.setResolved(true);
			}
			TrailParty pa = turn.partyById.get(a.getPartyId());
			TrailParty pb = turn.partyById.get(a.getOtherPartyId());
			if (b == null || pa == null || pb == null || turn.members(pa).isEmpty() || turn.members(pb).isEmpty()) {
				choices.save(a);
				if (b != null) choices.save(b);
				continue;
			}
			meet(turn, a, pa, b, pb);
			choices.save(a);
			choices.save(b);
		}
	}

	private void meet(Turn turn, TrailChoice a, TrailParty pa, TrailChoice b, TrailParty pb) {
		String ansA = a.getAnswer() == null ? "WAVE" : a.getAnswer();
		String ansB = b.getAnswer() == null ? "WAVE" : b.getAnswer();
		boolean stealA = ansA.startsWith("STEAL"), stealB = ansB.startsWith("STEAL");
		boolean helpA = ansA.startsWith("HELP"), helpB = ansB.startsWith("HELP");

		if (stealA && stealB) {
			for (TrailChoice c : List.of(a, b)) {
				TrailPlayer thief = turn.player(c.getAnsweredBy());
				if (thief != null) {
					thief.setStickyFingers(true);
					addKarma(thief, TrailOdds.THEFT_KARMA, turn.t);
				}
				c.setAmount(-1);
			}
			String text = turn.names(pa) + " and " + turn.names(pb)
				+ " both reached for each other's Glazers at the same time. Nothing changed hands. Everyone is embarrassed, and they will never speak again.";
			turn.story(null, TrailLog.Kind.THEFT, text);
			push.notify(concat(turn.ids(pa), turn.ids(pb)), turn.game.getName(), text, PAGE);
			return;
		}
		if (helpA) help(turn, a, pa, pb, ansA);
		if (helpB) help(turn, b, pb, pa, ansB);
		if (stealA) steal(turn, a, pa, pb, ansA, helpB);
		if (stealB) steal(turn, b, pb, pa, ansB, helpA);
		if (helpA && helpB) {
			pa.setLuck(TrailOdds.MAX_SHIFT);
			pb.setLuck(TrailOdds.MAX_SHIFT);
			turn.story(null, TrailLog.Kind.HELP, turn.names(pa) + " and " + turn.names(pb)
				+ " helped each other out. The trail is a little kinder to both of them for a turn.");
		}
		if (!helpA && !helpB && !stealA && !stealB) {
			turn.story(pa, TrailLog.Kind.STORY, "You waved at " + turn.names(pb) + ". They waved back.");
			turn.story(pb, TrailLog.Kind.STORY, "You waved at " + turn.names(pa) + ". They waved back.");
		}
	}

	private void help(Turn turn, TrailChoice c, TrailParty giver, TrailParty taker, String how) {
		TrailPlayer helper = turn.player(c.getAnsweredBy());
		String helperName = turn.nameOf(c.getAnsweredBy());
		String gave = null;
		switch (how) {
			case "HELP_FOOD" -> {
				int n = Math.min(TrailOdds.HELP_FOOD_MAX, giver.getFood());
				if (n > 0) {
					giver.setFood(giver.getFood() - n);
					taker.setFood(taker.getFood() + n);
					gave = n + " snack " + (n == 1 ? "pack" : "packs");
				}
			}
			case "HELP_COINS" -> {
				int n = helper == null ? 0 : Math.min(TrailOdds.HELP_COIN_MAX, helper.getCoins());
				if (n > 0) {
					helper.setCoins(helper.getCoins() - n);
					give(turn.members(taker), n);
					gave = n + " coins";
				}
			}
			case "HELP_WAGON" -> {
				if (taker.isWagonBroken()) {
					int cost = (int) Math.round(turn.price(Item.REPAIR) * TrailOdds.HELP_REPAIR_SHARE);
					Map<TrailPlayer, Integer> paid = charge(turn.members(giver), cost);
					if (paid != null) {
						logPurchases(turn.game, giver, turn.t, paid, "fixing " + turn.names(taker) + "'s wagon");
						taker.setWagonBroken(false);
						gave = "a wagon repair";
					}
				}
			}
			case "HELP_WHEEL" -> {
				if (giver.getSpareWheels() > 0) {
					giver.setSpareWheels(giver.getSpareWheels() - 1);
					taker.setSpareWheels(taker.getSpareWheels() + 1);
					useSpareWheel(taker);
					gave = "a spare wheel";
				}
			}
			default -> { }
		}
		if (gave == null) {
			turn.story(null, TrailLog.Kind.HELP, helperName + " wanted to help " + turn.names(taker)
				+ " but had nothing to give. The thought counts, a little.");
			return;
		}
		if (helper != null) {
			addKarma(helper, TrailOdds.HELP_KARMA, turn.t);
			helper.setSamaritanUntil(turn.t + TrailOdds.SAMARITAN_TURNS);
		}
		turn.story(null, TrailLog.Kind.HELP, helperName + " gave " + turn.names(taker) + " " + gave + ". A Good Samaritan.");
		push.notify(turn.ids(taker), turn.game.getName(), helperName + " gave you " + gave + ".", PAGE);
	}

	private void steal(Turn turn, TrailChoice c, TrailParty thieves, TrailParty target, String what, boolean targetHelped) {
		TrailPlayer thief = turn.player(c.getAnsweredBy());
		if (thief == null) return;
		String thiefName = turn.nameOf(thief.getUserId());
		addKarma(thief, TrailOdds.THEFT_KARMA, turn.t);
		boolean topThird = topThird(turn, thieves);
		List<TrailPlayer> victims = turn.members(target);

		int coins = 0, food = 0;
		Item part = null;
		switch (what) {
			case "STEAL_COINS" -> {
				int pool = victims.stream().mapToInt(TrailPlayer::getCoins).sum();
				coins = Math.min(TrailOdds.STEAL_COIN_MAX, (int) Math.round(pool * TrailOdds.STEAL_COIN_SHARE));
				if (topThird) coins /= 2;
			}
			case "STEAL_FOOD" -> {
				food = Math.min(TrailOdds.STEAL_FOOD_MAX, target.getFood());
				if (topThird) food /= 2;
			}
			default -> part = target.getSpareWheels() > 0 ? Item.SPARE_WHEEL : target.getHorseshoes() > 0 ? Item.HORSESHOES
				: target.getCarrots() > 0 ? Item.CARROTS : null;
		}
		String haul = coins > 0 ? coins + " coins" : food > 0 ? food + " snack " + (food == 1 ? "pack" : "packs")
			: part != null ? "their " + part.label.toLowerCase() : null;
		if (haul == null) {
			turn.story(null, TrailLog.Kind.THEFT, thiefName + " tried to rob " + turn.names(target) + ", but they had nothing worth taking.");
			return;
		}
		int haulValue = coins + food * turn.price(Item.FOOD) + (part == null ? 0 : turn.price(part));
		int chance = TrailOdds.stealChance(worth(turn, thieves), worth(turn, target), targetHelped, target.isWagonBroken());
		if (roll(chance)) {
			if (coins > 0) give(List.of(thief), sum(take(victims, coins)));
			if (food > 0) {
				target.setFood(target.getFood() - food);
				thieves.setFood(thieves.getFood() + food);
			}
			if (part != null) movePart(target, thieves, part);
			String text = thiefName + " robbed " + turn.names(target) + " of " + haul + (topThird ? " (and felt a little bad about it)" : "") + ".";
			turn.story(null, TrailLog.Kind.THEFT, text);
			push.notify(turn.ids(target), turn.game.getName(), thiefName + " robbed you of " + haul + ".", PAGE);
			return;
		}
		thief.setStickyFingers(true);
		int fine = Math.min(thief.getCoins(), haulValue / 2);
		if (fine > 0) {
			thief.setCoins(thief.getCoins() - fine);
			give(victims, fine);
		}
		turn.move.merge(thieves.getId(), 0.5, (x, y) -> x * y);
		String text = thiefName + " tried to rob " + turn.names(target) + " of " + haul + " and got caught. "
			+ (fine > 0 ? "Paid them " + fine + " coins and " : "") + "lost half a turn explaining themselves to a Montana sheriff.";
		turn.story(null, TrailLog.Kind.THEFT, text);
		push.notify(concat(turn.ids(target), List.of(thief.getUserId())), turn.game.getName(), text, PAGE);
	}

	/** Coins plus the shop value of food and spare parts. Animals and wagons don't count. */
	private int worth(Turn turn, TrailParty p) {
		return turn.members(p).stream().mapToInt(TrailPlayer::getCoins).sum()
			+ p.getFood() * turn.price(Item.FOOD) + p.getSpareWheels() * turn.price(Item.SPARE_WHEEL)
			+ p.getHorseshoes() * turn.price(Item.HORSESHOES) + p.getCarrots() * turn.price(Item.CARROTS);
	}

	private boolean topThird(Turn turn, TrailParty p) {
		List<TrailParty> order = standings(turn.partyById.values().stream().filter(o -> !turn.members(o).isEmpty()).toList());
		int index = order.indexOf(p);
		return index >= 0 && index < Math.ceil(order.size() / 3.0) && order.size() >= 3;
	}

	/** Skims, invites, get-evens, and votes nobody answered in time. */
	private void expireChoices(Turn turn) {
		for (TrailChoice c : choices.findByGameIdAndResolvedFalse(turn.game.getId())) {
			if (c.getExpiresTurn() >= turn.t) continue;
			c.setResolved(true);
			choices.save(c);
		}
	}

	// What players do.

	@Transactional
	public GameView choose(User viewer, Long gameId, int option) {
		TrailGame g = live(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty p = parties.findById(me.getPartyId()).orElseThrow();
		Scenario sc = p.getScenarioKey() == null ? null : TrailScenarios.BY_KEY.get(p.getScenarioKey());
		if (sc == null) throw bad("There's nothing to decide right now");
		if (option < 0 || option >= sc.options().size()) throw bad("Pick one of the options");
		List<TrailPlayer> members = players.findByPartyId(p.getId());
		Option op = sc.options().get(option);
		if (!available(op, p, members)) throw bad(unavailable(op));
		p.setScenarioAnswer(option);
		p.setAnsweredBy(viewer.getUserId());
		parties.save(p);
		return view(g, viewer);
	}

	@Transactional
	public GameView buy(User viewer, Long gameId, BuyInput input) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty p = parties.findById(me.getPartyId()).orElseThrow();
		if (p.getArrivedTurn() != null) throw bad("You're at the coast. Nothing to buy but saltwater taffy.");
		if (input == null || input.item() == null) throw bad("Pick something to buy");
		Item item = input.item();
		int quantity = input.quantity() == null ? 1 : input.quantity();
		int max = item == Item.FOOD ? 60 : switch (item) {
			case SPARE_WHEEL, HORSESHOES, CARROTS, PLAYLIST -> 5;
			default -> 1;
		};
		if (quantity < 1 || quantity > max) throw bad("You can buy 1 to " + max + " of those at a time");
		switch (item) {
			case DONKEY -> {
				if (me.getMount() != Mount.WALK) throw bad("You already have a ride");
			}
			case HORSE -> {
				if (me.getMount() == Mount.HORSE) throw bad("You already have a horse");
			}
			case WAGON -> {
				if (p.isWagon()) throw bad("Your party already has a wagon");
			}
			case REPAIR -> {
				if (!p.isWagonBroken()) throw bad("Nothing is broken. Yet.");
			}
			default -> { }
		}
		List<TrailPlayer> members = players.findByPartyId(p.getId());
		// Animals belong to whoever rides them, so the rider pays alone.
		boolean animal = item == Item.DONKEY || item == Item.HORSE;
		boolean split = Boolean.TRUE.equals(input.split()) && members.size() > 1 && !animal;
		int cost = TrailOdds.price(item, g.getPriceScale()) * quantity;
		Map<TrailPlayer, Integer> paid = charge(split ? members : List.of(me), cost);
		if (paid == null) throw bad(split ? "The party doesn't have " + cost + " coins between you" : "You don't have " + cost + " coins");
		switch (item) {
			case FOOD -> {
				p.setFood(p.getFood() + quantity);
				p.setHangry(false);
			}
			case DONKEY -> me.setMount(Mount.DONKEY);
			case HORSE -> {
				me.setMount(Mount.HORSE);
				me.setShoeThrown(false);
			}
			case WAGON -> {
				p.setWagon(true);
				p.setWagonBroken(false);
			}
			case REPAIR -> p.setWagonBroken(false);
			case SPARE_WHEEL -> p.setSpareWheels(p.getSpareWheels() + quantity);
			case HORSESHOES -> p.setHorseshoes(p.getHorseshoes() + quantity);
			case CARROTS -> p.setCarrots(p.getCarrots() + quantity);
			case PLAYLIST -> p.setPlaylists(p.getPlaylists() + quantity);
		}
		useSpareWheel(p);
		fixShoes(p, members);
		parties.save(p);
		players.saveAll(members);
		players.save(me);
		logPurchases(g, p, g.getTurn(), paid, (quantity > 1 ? quantity + " x " : "") + item.label);
		return view(g, viewer);
	}

	@Transactional
	public GameView setPace(User viewer, Long gameId, Pace pace) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		if (pace == null) throw bad("Pick a pace");
		TrailParty p = parties.findById(me.getPartyId()).orElseThrow();
		p.setPace(pace);
		parties.save(p);
		return view(g, viewer);
	}

	@Transactional
	public GameView abandonWagon(User viewer, Long gameId) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty p = parties.findById(me.getPartyId()).orElseThrow();
		if (!p.isWagon()) throw bad("There's no wagon to abandon");
		p.setWagon(false);
		p.setWagonBroken(false);
		parties.save(p);
		addLog(g, p.getId(), null, g.getTurn(), TrailLog.Kind.PARTY, userName(viewer) + " left the wagon by the side of the road. Someone will make it a planter.", null);
		return view(g, viewer);
	}

	@Transactional
	public GameView invite(User viewer, Long gameId, Long userId) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailPlayer them = userId == null ? null : players.findByGameIdAndUserId(gameId, userId).orElse(null);
		if (them == null || them.getUserId().equals(me.getUserId())) throw bad("Pick someone else on the trail");
		checkJoin(g, me, them);
		boolean already = choices.findByGameIdAndResolvedFalse(gameId).stream().anyMatch(c -> c.getKind() == TrailChoice.Kind.INVITE
			&& them.getUserId().equals(c.getUserId()) && me.getPartyId().equals(c.getPartyId()));
		if (already) throw bad("You've already invited them");
		TrailChoice c = choice(g, TrailChoice.Kind.INVITE, g.getTurn(), g.getTurn() + TrailOdds.INVITE_TURNS);
		c.setUserId(them.getUserId());
		c.setOtherUserId(me.getUserId());
		c.setPartyId(me.getPartyId());
		choices.save(c);
		push.notify(List.of(them.getUserId()), g.getName(), userName(viewer) + " wants you to join their party on the trail.", PAGE);
		return view(g, viewer);
	}

	/** Whether {@code them} (alone) can join {@code me}'s party now. */
	private void checkJoin(TrailGame g, TrailPlayer me, TrailPlayer them) {
		TrailParty mine = parties.findById(me.getPartyId()).orElseThrow();
		TrailParty theirs = parties.findById(them.getPartyId()).orElseThrow();
		if (mine.getId().equals(theirs.getId())) throw bad("You're already traveling together");
		if (mine.getArrivedTurn() != null || theirs.getArrivedTurn() != null) throw bad("Nobody forms parties at the coast");
		if (players.findByPartyId(theirs.getId()).size() > 1) throw bad("They're already in a party. They'd have to leave it first.");
		if (players.findByPartyId(mine.getId()).size() >= TrailOdds.MAX_PARTY) throw bad("A party can have " + TrailOdds.MAX_PARTY + " people at most");
		if (Math.abs(mine.getMiles() - theirs.getMiles()) > TrailOdds.INVITE_MILES) {
			throw bad("You have to be within " + TrailOdds.INVITE_MILES + " miles of each other");
		}
		if (me.getNoJoinUntil() > g.getTurn()) throw bad("You need some space until turn " + me.getNoJoinUntil());
		if (them.getNoJoinUntil() > g.getTurn()) throw bad("They need some space until turn " + them.getNoJoinUntil());
	}

	/** Answer an invite, a chance to skim, a meeting, getting even, or a vote. */
	@Transactional
	public GameView answer(User viewer, Long gameId, Long choiceId, String answer) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailChoice c = choices.findById(choiceId).filter(x -> x.getGameId().equals(gameId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That's not a question anymore"));
		boolean mine = c.getKind() == TrailChoice.Kind.MEET ? me.getPartyId().equals(c.getPartyId()) : me.getUserId().equals(c.getUserId());
		if (!mine) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That question isn't yours");
		if (c.isResolved() || c.getExpiresTurn() < g.getTurn()) throw bad("Too late. That moment has passed.");
		String a = answer == null ? "" : answer.trim().toUpperCase();
		switch (c.getKind()) {
			case INVITE -> answerInvite(g, me, c, a);
			case SKIM -> skim(g, me, c, a);
			case MEET -> {
				if (!List.of("WAVE", "HELP_FOOD", "HELP_COINS", "HELP_WAGON", "HELP_WHEEL", "STEAL_COINS", "STEAL_FOOD", "STEAL_PARTS").contains(a)) {
					throw bad("Pick one of the options");
				}
				c.setAnswer(a);
				c.setAnsweredBy(me.getUserId());
				choices.save(c);
			}
			case GET_EVEN -> getEven(g, me, c, a);
			case VOTE_OUT -> voteOut(g, me, c, a);
		}
		return view(g, viewer);
	}

	private void answerInvite(TrailGame g, TrailPlayer me, TrailChoice c, String a) {
		if (!a.equals("ACCEPT") && !a.equals("DECLINE")) throw bad("Accept or decline");
		c.setAnswer(a);
		c.setAnsweredBy(me.getUserId());
		c.setResolved(true);
		choices.save(c);
		TrailPlayer inviter = players.findByGameIdAndUserId(g.getId(), c.getOtherUserId()).orElse(null);
		String myName = userName(me.getUserId());
		if (a.equals("DECLINE")) {
			if (inviter != null) push.notify(List.of(inviter.getUserId()), g.getName(), myName + " would rather go it alone. For now.", PAGE);
			return;
		}
		if (inviter == null || !inviter.getPartyId().equals(c.getPartyId())) throw bad("That party isn't together anymore");
		checkJoin(g, inviter, me);
		TrailParty party = parties.findById(inviter.getPartyId()).orElseThrow();
		TrailParty old = parties.findById(me.getPartyId()).orElseThrow();
		String note = "";
		party.setMiles(Math.min(party.getMiles(), old.getMiles()));
		party.setFood(party.getFood() + old.getFood());
		party.setSpareWheels(party.getSpareWheels() + old.getSpareWheels());
		party.setHorseshoes(party.getHorseshoes() + old.getHorseshoes());
		party.setCarrots(party.getCarrots() + old.getCarrots());
		party.setPlaylists(party.getPlaylists() + old.getPlaylists());
		party.setPuppy(party.isPuppy() || old.isPuppy());
		if (old.isWagon() && party.isWagon()) {
			int back = (int) Math.round(TrailOdds.price(Item.WAGON, g.getPriceScale()) * TrailOdds.WAGON_RESALE);
			me.setCoins(me.getCoins() + back);
			note = " One wagon too many: " + myName + " sold theirs back for " + back + " coins.";
		} else if (old.isWagon()) {
			party.setWagon(true);
			party.setWagonBroken(old.isWagonBroken());
		}
		if (party.getFood() > 0) party.setHangry(false);
		useSpareWheel(party);
		me.setPartyId(party.getId());
		me.setJoinedTurn(g.getTurn());
		parties.save(party);
		players.save(me);
		for (TrailChoice other : choices.findByGameIdAndResolvedFalse(g.getId())) {
			if (old.getId().equals(other.getPartyId()) || old.getId().equals(other.getOtherPartyId())) {
				other.setResolved(true);
				choices.save(other);
			}
		}
		parties.delete(old);
		List<Long> everyone = players.findByPartyId(party.getId()).stream().map(TrailPlayer::getUserId).toList();
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.PARTY, myName + " joined " + userName(inviter.getUserId())
			+ "'s party at mile " + party.getMiles() + "." + note, null);
		push.notify(everyone.stream().filter(id -> !id.equals(me.getUserId())).toList(), g.getName(), myName + " joined your party.", PAGE);
	}

	/** Leave your party: keep your coins and your animal, take your share of the food, and need some space. */
	@Transactional
	public GameView leave(User viewer, Long gameId) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty party = parties.findById(me.getPartyId()).orElseThrow();
		List<TrailPlayer> members = players.findByPartyId(party.getId());
		if (members.size() < 2) throw bad("You're already on your own");
		if (party.getArrivedTurn() != null) throw bad("You're at the coast. Just move your towel.");
		int share = party.getFood() / members.size();
		split(g, me, party, share, TrailOdds.SPACE_TURNS);
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.PARTY, userName(viewer) + " left their party at mile " + party.getMiles()
			+ " with " + share + " snack " + (share == 1 ? "pack" : "packs") + ". It's not you, it's the trail.", null);
		push.notify(members.stream().map(TrailPlayer::getUserId).filter(id -> !id.equals(viewer.getUserId())).toList(),
			g.getName(), userName(viewer) + " left your party.", PAGE);
		return view(g, viewer);
	}

	/** Put {@code who} in a party of their own where {@code from} is, with {@code food} of its food. */
	private void split(TrailGame g, TrailPlayer who, TrailParty from, int food, int spaceTurns) {
		TrailParty alone = new TrailParty();
		alone.setGameId(g.getId());
		alone.setMiles(from.getMiles());
		alone.setFood(food);
		alone.setSeen(from.getSeen());
		from.setFood(from.getFood() - food);
		parties.save(from);
		alone = parties.save(alone);
		who.setPartyId(alone.getId());
		who.setNoJoinUntil(g.getTurn() + spaceTurns);
		who.setJoinedTurn(g.getTurn());
		players.save(who);
		for (TrailChoice c : choices.findByGameIdAndResolvedFalse(g.getId())) {
			boolean theirs = (c.getKind() == TrailChoice.Kind.SKIM || c.getKind() == TrailChoice.Kind.VOTE_OUT)
				&& who.getUserId().equals(c.getUserId()) && from.getId().equals(c.getPartyId());
			if (theirs) {
				c.setResolved(true);
				choices.save(c);
			}
		}
		if (g.getFinishedAt() == null && g.getTurn() > 0 && g.getTurn() < g.getTurns()) {
			Turn turn = new Turn(g);
			TrailParty mine = turn.partyById.get(alone.getId());
			deal(turn, mine);
			parties.save(mine);
		}
	}

	private void skim(TrailGame g, TrailPlayer me, TrailChoice c, String a) {
		if (!List.of("COINS", "FOOD", "PARTS", "NO").contains(a)) throw bad("Pick one of the options");
		c.setAnswer(a);
		c.setAnsweredBy(me.getUserId());
		c.setResolved(true);
		choices.save(c);
		if (a.equals("NO")) return;
		TrailParty party = parties.findById(me.getPartyId()).orElseThrow();
		if (!party.getId().equals(c.getPartyId())) return;
		List<TrailPlayer> victims = players.findByPartyId(party.getId()).stream().filter(pl -> !pl.getId().equals(me.getId())).toList();
		if (victims.isEmpty()) return;
		double scale = g.getPriceScale();

		Map<TrailPlayer, Integer> takenFrom = new HashMap<>();
		String what;
		int value;
		switch (a) {
			case "COINS" -> {
				int pool = victims.stream().mapToInt(TrailPlayer::getCoins).sum();
				int n = Math.min(TrailOdds.SKIM_COIN_MAX, (int) Math.round(pool * TrailOdds.SKIM_COIN_SHARE));
				if (n <= 0) throw bad("There's nothing in the kitty to skim");
				takenFrom = take(victims, n);
				value = sum(takenFrom);
				me.setCoins(me.getCoins() + value);
				what = value + " coins";
			}
			case "FOOD" -> {
				int n = Math.min(TrailOdds.SKIM_FOOD_MAX, party.getFood());
				if (n <= 0) throw bad("There are no snack packs to take");
				party.setFood(party.getFood() - n);
				value = n * TrailOdds.price(Item.FOOD, scale);
				me.setCoins(me.getCoins() + value);
				what = n + " snack " + (n == 1 ? "pack" : "packs") + " (sold to a trucker for " + value + " coins)";
			}
			default -> {
				Item part = party.getSpareWheels() > 0 ? Item.SPARE_WHEEL : party.getHorseshoes() > 0 ? Item.HORSESHOES : null;
				if (part == null) throw bad("There are no spare parts to take");
				if (part == Item.SPARE_WHEEL) party.setSpareWheels(party.getSpareWheels() - 1);
				else party.setHorseshoes(party.getHorseshoes() - 1);
				value = TrailOdds.price(part, scale);
				me.setCoins(me.getCoins() + value);
				what = "the " + part.label.toLowerCase() + " (sold for " + value + " coins)";
			}
		}
		if (takenFrom.isEmpty()) {
			int each = value / victims.size();
			for (TrailPlayer v : victims) takenFrom.put(v, each);
		}
		addKarma(me, TrailOdds.THEFT_KARMA, g.getTurn());
		boolean caught = roll(TrailOdds.SKIM_CAUGHT);
		String myName = userName(me.getUserId());
		String text;
		if (caught) {
			me.setStickyFingers(true);
			int back = Math.min(me.getCoins(), value / 2);
			me.setCoins(me.getCoins() - back);
			give(victims, back);
			text = myName + " took " + what + " from the party. Caught with crumbs on their cape, and gave back " + back + " coins.";
		} else {
			text = "Someone took " + what + " from the party. The Glazers are also missing.";
		}
		parties.save(party);
		players.saveAll(victims);
		players.save(me);
		addLog(g, party.getId(), null, g.getTurn(), TrailLog.Kind.THEFT, text, null);
		push.notify(victims.stream().map(TrailPlayer::getUserId).toList(), g.getName(), text, PAGE);
		for (TrailPlayer v : victims) {
			TrailChoice even = choice(g, TrailChoice.Kind.GET_EVEN, g.getTurn(), g.getTurn() + TrailOdds.AFTERMATH_TURNS);
			even.setUserId(v.getUserId());
			even.setPartyId(party.getId());
			even.setOtherUserId(me.getUserId());
			even.setAmount(Math.max(1, takenFrom.getOrDefault(v, 0)));
			choices.save(even);
			if (caught) {
				TrailChoice vote = choice(g, TrailChoice.Kind.VOTE_OUT, g.getTurn(), g.getTurn() + TrailOdds.AFTERMATH_TURNS);
				vote.setUserId(v.getUserId());
				vote.setPartyId(party.getId());
				vote.setOtherUserId(me.getUserId());
				choices.save(vote);
			}
		}
	}

	private void getEven(TrailGame g, TrailPlayer me, TrailChoice c, String a) {
		if (!a.equals("YES") && !a.equals("NO")) throw bad("Yes or no");
		c.setAnswer(a);
		c.setAnsweredBy(me.getUserId());
		c.setResolved(true);
		choices.save(c);
		if (a.equals("NO")) return;
		TrailPlayer thief = players.findByGameIdAndUserId(g.getId(), c.getOtherUserId()).orElse(null);
		if (thief == null) return;
		String myName = userName(me.getUserId());
		String text;
		if (roll(TrailOdds.GET_EVEN_WORKS)) {
			int n = Math.min(thief.getCoins(), c.getAmount() * 2);
			thief.setCoins(thief.getCoins() - n);
			me.setCoins(me.getCoins() + n);
			text = myName + " got even with " + userName(thief.getUserId()) + " and took back " + n + " coins. Karma's a stylist too.";
		} else {
			me.setStickyFingers(true);
			addKarma(me, TrailOdds.THEFT_KARMA, g.getTurn());
			text = myName + " tried to get even and got caught. Now everyone has Sticky Fingers.";
		}
		players.save(thief);
		players.save(me);
		addLog(g, c.getPartyId(), null, g.getTurn(), TrailLog.Kind.THEFT, text, null);
		push.notify(List.of(thief.getUserId()), g.getName(), text, PAGE);
	}

	private void voteOut(TrailGame g, TrailPlayer me, TrailChoice c, String a) {
		if (!a.equals("YES") && !a.equals("NO")) throw bad("Yes or no");
		c.setAnswer(a);
		c.setAnsweredBy(me.getUserId());
		c.setResolved(true);
		choices.save(c);
		if (a.equals("NO")) return;
		TrailPlayer thief = players.findByGameIdAndUserId(g.getId(), c.getOtherUserId()).orElse(null);
		if (thief == null || !thief.getPartyId().equals(c.getPartyId())) return;
		TrailParty party = parties.findById(c.getPartyId()).orElseThrow();
		split(g, thief, party, 0, TrailOdds.SPACE_TURNS * 2);
		for (TrailChoice other : choices.findByGameIdAndResolvedFalse(g.getId())) {
			if (other.getKind() == TrailChoice.Kind.VOTE_OUT && thief.getUserId().equals(other.getOtherUserId())) {
				other.setResolved(true);
				choices.save(other);
			}
		}
		String text = userName(me.getUserId()) + " voted " + userName(thief.getUserId())
			+ " out of the party. They walk off with nothing but their Sticky Fingers.";
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.PARTY, text, null);
		push.notify(List.of(thief.getUserId()), g.getName(), "You've been voted out of your party.", PAGE);
	}

	// Coins and parts.

	/**
	 * Takes {@code cost} from these people, split as evenly as their coins allow (whoever has
	 * less pays what they can, the rest cover the difference). Null, and nothing taken, if
	 * they don't have it between them.
	 */
	static Map<TrailPlayer, Integer> charge(List<TrailPlayer> payers, int cost) {
		if (payers.stream().mapToInt(TrailPlayer::getCoins).sum() < cost) return null;
		return take(payers, cost);
	}

	/** Like {@link #charge}, but takes what there is when there isn't enough. */
	static Map<TrailPlayer, Integer> take(List<TrailPlayer> from, int amount) {
		List<TrailPlayer> order = from.stream().filter(pl -> pl.getCoins() > 0)
			.sorted(Comparator.comparingInt(TrailPlayer::getCoins)).toList();
		Map<TrailPlayer, Integer> out = new LinkedHashMap<>();
		int remaining = amount;
		for (int i = 0; i < order.size() && remaining > 0; i++) {
			TrailPlayer pl = order.get(i);
			int share = (remaining + (order.size() - i) - 1) / (order.size() - i);
			int n = Math.min(share, pl.getCoins());
			pl.setCoins(pl.getCoins() - n);
			out.put(pl, n);
			remaining -= n;
		}
		return out;
	}

	/** Split coins evenly; the first few get the leftovers. */
	static void give(List<TrailPlayer> to, int amount) {
		if (to.isEmpty() || amount <= 0) return;
		int each = amount / to.size();
		int extra = amount % to.size();
		for (int i = 0; i < to.size(); i++) {
			TrailPlayer pl = to.get(i);
			pl.setCoins(pl.getCoins() + each + (i < extra ? 1 : 0));
		}
	}

	private static int sum(Map<TrailPlayer, Integer> paid) {
		return paid.values().stream().mapToInt(Integer::intValue).sum();
	}

	private void logPurchases(TrailGame g, TrailParty p, int turn, Map<TrailPlayer, Integer> paid, String what) {
		for (Map.Entry<TrailPlayer, Integer> e : paid.entrySet()) {
			if (e.getValue() <= 0) continue;
			addLog(g, p.getId(), e.getKey().getUserId(), turn, TrailLog.Kind.PURCHASE,
				userName(e.getKey().getUserId()) + " paid " + e.getValue() + " for " + what, e.getValue());
		}
	}

	/** A broken wagon with a spare wheel on hand gets fixed. */
	private static void useSpareWheel(TrailParty p) {
		if (p.isWagonBroken() && p.getSpareWheels() > 0) {
			p.setSpareWheels(p.getSpareWheels() - 1);
			p.setWagonBroken(false);
		}
	}

	/** Horses that threw a shoe get one from the party's horseshoes. True if any were fixed. */
	private static boolean fixShoes(TrailParty p, List<TrailPlayer> members) {
		boolean fixed = false;
		for (TrailPlayer pl : members) {
			if (!pl.isShoeThrown() || p.getHorseshoes() <= 0) continue;
			p.setHorseshoes(p.getHorseshoes() - 1);
			pl.setShoeThrown(false);
			fixed = true;
		}
		return fixed;
	}

	private static void movePart(TrailParty from, TrailParty to, Item part) {
		switch (part) {
			case SPARE_WHEEL -> {
				from.setSpareWheels(from.getSpareWheels() - 1);
				to.setSpareWheels(to.getSpareWheels() + 1);
				useSpareWheel(to);
			}
			case HORSESHOES -> {
				from.setHorseshoes(from.getHorseshoes() - 1);
				to.setHorseshoes(to.getHorseshoes() + 1);
			}
			case CARROTS -> {
				from.setCarrots(from.getCarrots() - 1);
				to.setCarrots(to.getCarrots() + 1);
			}
			default -> { }
		}
	}

	private static boolean available(Option op, TrailParty p, List<TrailPlayer> members) {
		boolean has = switch (op.needs) {
			case NOTHING -> true;
			case WAGON -> p.isWagon();
			case CARROTS -> p.getCarrots() > 0;
			case PLAYLIST -> p.getPlaylists() > 0;
		};
		return has;
	}

	private static String unavailable(Option op) {
		return op.needs == Needs.WAGON ? "You need a wagon for that" : op.needs == Needs.CARROTS ? "You don't have any carrots"
			: op.needs == Needs.PLAYLIST ? "You don't have the Playlist" : "You can't pick that";
	}

	// Karma.

	private static int karmaNow(TrailPlayer pl, int turn) {
		return pl.getKarmaUntil() >= turn ? pl.getKarma() : 0;
	}

	private static void addKarma(TrailPlayer pl, int points, int turn) {
		int now = karmaNow(pl, turn) + points;
		pl.setKarma(Math.max(-TrailOdds.MAX_KARMA, Math.min(TrailOdds.MAX_KARMA, now)));
		pl.setKarmaUntil(turn + TrailOdds.KARMA_TURNS);
	}

	private boolean roll(int chance) {
		return random.nextInt(100) < chance;
	}

	// Setting up.

	/** Shop prices follow the group's usual daily service sales in the weeks before the start. */
	private double priceScale(List<User> group, LocalDate start) {
		LocalDate startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
		LocalDate firstWeek = startWeek.minusWeeks(ContestService.HISTORY_WEEKS);
		double sales = 0;
		int weeks = 0;
		for (User u : group) {
			for (WeeklyStat w : history(u, firstWeek, startWeek)) {
				if (w.getNetServiceSales() == null) continue;
				sales += w.getNetServiceSales().doubleValue();
				weeks++;
			}
		}
		if (weeks == 0 || sales <= 0) return 1;
		double daily = sales / weeks / 5;
		double scale = Math.max(TrailOdds.MIN_PRICE_SCALE, Math.min(TrailOdds.MAX_PRICE_SCALE, daily / TrailOdds.BASE_DAILY_SALES));
		return Math.round(scale * 20) / 20.0;
	}

	/**
	 * Handicaps like contests: HOURS gives more coins per dollar to people who work fewer hours,
	 * RATE to people whose usual sales per hour are lower, BOTH multiplies the two. 100 to 200.
	 */
	private Map<Long, Integer> worths(Handicap handicap, LocalDate start, List<User> group) {
		LocalDate startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
		LocalDate firstWeek = startWeek.minusWeeks(ContestService.HISTORY_WEEKS);
		Map<Long, Double> hours = new HashMap<>();
		Map<Long, Double> rate = new HashMap<>();
		for (User u : group) {
			List<WeeklyStat> weeks = history(u, firstWeek, startWeek);
			if (weeks.isEmpty()) continue;
			double floorHours = weeks.stream().mapToDouble(w -> w.getFloorHours() == null ? 0 : w.getFloorHours().doubleValue()).sum();
			double sales = weeks.stream().mapToDouble(w -> w.getNetServiceSales() == null ? 0 : w.getNetServiceSales().doubleValue()).sum();
			if (floorHours > 0) {
				hours.put(u.getUserId(), floorHours / weeks.size());
				rate.put(u.getUserId(), sales / floorHours);
			}
		}
		double averageHours = hours.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double averageRate = rate.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
		Map<Long, Integer> out = new HashMap<>();
		for (User u : group) {
			double h = ContestService.boost(averageHours, hours.get(u.getUserId()));
			double r = ContestService.boost(averageRate, rate.get(u.getUserId()));
			out.put(u.getUserId(), ContestService.toWorth(switch (handicap) {
				case HOURS -> h;
				case RATE -> r;
				default -> h * r;
			}));
		}
		return out;
	}

	private List<WeeklyStat> history(User u, LocalDate firstWeek, LocalDate startWeek) {
		if (u.getSalonUltimateName() == null) return List.of();
		return stats.findBySuNameIgnoreCaseOrderByWeekStartDesc(u.getSalonUltimateName()).stream()
			.filter(w -> !w.getWeekStart().isBefore(firstWeek) && w.getWeekStart().isBefore(startWeek))
			.toList();
	}

	private static int bonus(Integer value, int fallback) {
		if (value == null) return fallback;
		if (value < 0 || value > 500) throw bad("Keep each bonus between 0 and 500 coins");
		return value;
	}

	// Views.

	private GameView view(TrailGame g, User viewer) {
		Turn turn = new Turn(g);
		List<TrailParty> withPeople = turn.partyById.values().stream().filter(p -> !turn.members(p).isEmpty()).toList();
		List<TrailParty> order = standings(withPeople);
		Map<Long, Integer> places = places(order);
		List<PartyView> partyViews = order.stream().map(p -> {
			List<TrailPlayer> members = turn.members(p);
			List<MemberView> mv = members.stream().map(pl -> new MemberView(pl.getUserId(), turn.nameOf(pl.getUserId()),
				mountLabel(pl), labels(pl, g.getTurn()))).sorted(Comparator.comparing(m -> m.name().toLowerCase())).toList();
			return new PartyView(p.getId(), p.getMiles(), TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(p.getMiles())).name(),
				p.getArrivedTurn(), g.getTurn() > 0 ? places.get(p.getId()) : null, travel(p, members), p.isWagonBroken(), mv);
		}).toList();

		TrailPlayer me = turn.player(viewer.getUserId());
		MeView meView = me == null ? null : me(turn, me);
		Long myParty = me == null ? null : me.getPartyId();
		List<LogView> story = logs.findByGameIdOrderByIdDesc(g.getId()).stream()
			.filter(l -> l.getKind() != TrailLog.Kind.PURCHASE)
			.filter(l -> l.getPartyId() == null || l.getPartyId().equals(myParty))
			.limit(80)
			.map(l -> new LogView(l.getTurn(), l.getKind().name(), l.getBody(), l.getCoins()))
			.toList();
		List<ShopItem> shop = java.util.Arrays.stream(Item.values())
			.map(i -> new ShopItem(i.name(), i.label, TrailOdds.price(i, g.getPriceScale()), i.help)).toList();
		List<LandmarkView> landmarks = TrailOdds.LANDMARKS.stream().map(l -> new LandmarkView(l.name(), l.miles())).toList();
		String status = g.getFinishedAt() != null ? "FINISHED" : g.getTurn() == 0 ? "UPCOMING" : "LIVE";
		return new GameView(g.getId(), g.getName(), status, g.getTurn(), g.getTurns(), g.getStartDate(), g.getPrize(),
			g.getHandicap().name(), g.getBonusTreatment(), g.getBonusColor(), g.getBonusWax(), g.getLastCheckedAt(),
			viewer.isAdmin() ? g.getCheckProblem() : null,
			users.findById(g.getCreatedBy()).map(TrailService::name).orElse("Former employee"),
			TrailOdds.TRAIL_MILES, landmarks, partyViews, meView, shop, story);
	}

	private MeView me(Turn turn, TrailPlayer me) {
		TrailGame g = turn.game;
		TrailParty p = turn.partyById.get(me.getPartyId());
		List<TrailPlayer> members = turn.members(p);
		MyParty party = new MyParty(p.getId(), p.getFood(), p.getPace().name(), p.isHangry(), p.isWagon(), p.isWagonBroken(),
			p.getSpareWheels(), p.getHorseshoes(), p.getCarrots(), p.getPlaylists(), p.isPuppy(),
			members.stream().map(pl -> new MyMember(pl.getUserId(), turn.nameOf(pl.getUserId()), pl.getCoins(), pl.getMount().name(), pl.isShoeThrown()))
				.sorted(Comparator.comparing(m -> m.name().toLowerCase())).toList());

		ScenarioView scenario = null;
		Scenario sc = p.getScenarioKey() == null ? null : TrailScenarios.BY_KEY.get(p.getScenarioKey());
		if (sc != null) {
			List<OptionView> options = new ArrayList<>();
			for (int i = 0; i < sc.options().size(); i++) {
				Option op = sc.options().get(i);
				options.add(new OptionView(i, op.label, op.good.text, op.sure() ? null : op.bad.text, op.isDefault, available(op, p, members)));
			}
			scenario = new ScenarioView(sc.key(), sc.text(), options, p.getScenarioAnswer(),
				p.getAnsweredBy() == null ? null : turn.nameOf(p.getAnsweredBy()));
		}

		List<ChoiceView> mine = new ArrayList<>();
		for (TrailChoice c : choices.findByGameIdAndResolvedFalse(g.getId())) {
			if (c.getExpiresTurn() < g.getTurn()) continue;
			boolean forMe = c.getKind() == TrailChoice.Kind.MEET ? p.getId().equals(c.getPartyId()) : me.getUserId().equals(c.getUserId());
			if (!forMe) continue;
			ChoiceView v = choiceView(turn, c, p, me);
			if (v != null) mine.add(v);
		}

		List<InviteTarget> canInvite = new ArrayList<>();
		if (p.getArrivedTurn() == null && members.size() < TrailOdds.MAX_PARTY && me.getNoJoinUntil() <= g.getTurn() && g.getFinishedAt() == null) {
			for (TrailPlayer other : turn.everyone) {
				TrailParty theirs = turn.partyById.get(other.getPartyId());
				if (theirs == null || theirs.getId().equals(p.getId()) || theirs.getArrivedTurn() != null) continue;
				if (turn.members(theirs).size() != 1 || other.getNoJoinUntil() > g.getTurn()) continue;
				if (Math.abs(theirs.getMiles() - p.getMiles()) > TrailOdds.INVITE_MILES) continue;
				canInvite.add(new InviteTarget(other.getUserId(), turn.nameOf(other.getUserId()), theirs.getMiles()));
			}
			canInvite.sort(Comparator.comparing(i -> i.name().toLowerCase()));
		}

		List<LogView> purchases = logs.findByGameIdOrderByIdDesc(g.getId()).stream()
			.filter(l -> l.getKind() == TrailLog.Kind.PURCHASE && p.getId().equals(l.getPartyId()))
			.limit(40)
			.map(l -> new LogView(l.getTurn(), l.getKind().name(), l.getBody(), l.getCoins()))
			.toList();
		return new MeView(me.getUserId(), me.getCoins(), me.getMount().name(), me.isShoeThrown(), me.getNoJoinUntil(), party,
			scenario, mine, canInvite, purchases);
	}

	private ChoiceView choiceView(Turn turn, TrailChoice c, TrailParty p, TrailPlayer me) {
		String answeredBy = c.getAnsweredBy() == null ? null : turn.nameOf(c.getAnsweredBy());
		double scale = turn.game.getPriceScale();
		return switch (c.getKind()) {
			case INVITE -> new ChoiceView(c.getId(), "INVITE", turn.nameOf(c.getOtherUserId()) + " wants you to join their party",
				"You'd set off from wherever the rearmost of you is. Food and spare parts are pooled, and anyone can spend the party's coins on the party.",
				List.of(new ChoiceOption("ACCEPT", "Join them"), new ChoiceOption("DECLINE", "No thanks")), null, null, c.getExpiresTurn());
			case SKIM -> {
				List<ChoiceOption> opts = new ArrayList<>();
				int pool = turn.members(p).stream().filter(pl -> !pl.getId().equals(me.getId())).mapToInt(TrailPlayer::getCoins).sum();
				int coins = Math.min(TrailOdds.SKIM_COIN_MAX, (int) Math.round(pool * TrailOdds.SKIM_COIN_SHARE));
				if (coins > 0) opts.add(new ChoiceOption("COINS", "Pocket " + coins + " coins from the others"));
				if (p.getFood() > 0) opts.add(new ChoiceOption("FOOD", "Sell " + Math.min(TrailOdds.SKIM_FOOD_MAX, p.getFood()) + " snack packs to a trucker"));
				if (p.getSpareWheels() > 0) opts.add(new ChoiceOption("PARTS", "Sell the spare wheel"));
				else if (p.getHorseshoes() > 0) opts.add(new ChoiceOption("PARTS", "Sell the horseshoes"));
				opts.add(new ChoiceOption("NO", "Not today"));
				yield new ChoiceView(c.getId(), "SKIM", "Skim off the top?",
					"Nobody is watching the party's stuff right now. Only you can see this. If you're caught, everyone will know.",
					opts, null, null, c.getExpiresTurn());
			}
			case MEET -> {
				TrailParty them = turn.partyById.get(c.getOtherPartyId());
				if (them == null) yield null;
				List<ChoiceOption> opts = new ArrayList<>();
				opts.add(new ChoiceOption("WAVE", "Wave and keep going"));
				if (p.getFood() > 0) opts.add(new ChoiceOption("HELP_FOOD", "Give them up to " + TrailOdds.HELP_FOOD_MAX + " snack packs"));
				if (me.getCoins() > 0) opts.add(new ChoiceOption("HELP_COINS", "Give them up to " + TrailOdds.HELP_COIN_MAX + " of your coins"));
				if (them.isWagonBroken()) {
					int cost = (int) Math.round(TrailOdds.price(Item.REPAIR, scale) * TrailOdds.HELP_REPAIR_SHARE);
					opts.add(new ChoiceOption("HELP_WAGON", "Pay to fix their wagon (" + cost + " coins, you know a guy)"));
				}
				if (p.getSpareWheels() > 0) opts.add(new ChoiceOption("HELP_WHEEL", "Give them your spare wheel"));
				opts.add(new ChoiceOption("STEAL_COINS", "Steal some of their coins"));
				opts.add(new ChoiceOption("STEAL_FOOD", "Steal some of their snack packs"));
				opts.add(new ChoiceOption("STEAL_PARTS", "Steal their spare parts"));
				String where = TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(them.getMiles())).name();
				yield new ChoiceView(c.getId(), "MEET", "You ran into " + turn.names(them),
					"They're at mile " + them.getMiles() + ", past " + where + (them.isWagonBroken() ? ", with a broken-down wagon" : "")
						+ ". They're deciding what to do about you, too. It all happens at the next turn.",
					opts, c.getAnswer(), answeredBy, c.getExpiresTurn());
			}
			case GET_EVEN -> new ChoiceView(c.getId(), "GET_EVEN", "Get even?",
				"Someone took from you. You could take back double, if it works. If it doesn't, you're the one with Sticky Fingers.",
				List.of(new ChoiceOption("YES", "Get even"), new ChoiceOption("NO", "Let it go")), null, null, c.getExpiresTurn());
			case VOTE_OUT -> new ChoiceView(c.getId(), "VOTE_OUT", "Vote " + turn.nameOf(c.getOtherUserId()) + " out of the party?",
				"They'd leave with no food and couldn't join anyone for " + TrailOdds.SPACE_TURNS * 2 + " turns.",
				List.of(new ChoiceOption("YES", "Vote them out"), new ChoiceOption("NO", "Give them another chance")), null, null, c.getExpiresTurn());
		};
	}

	/** At the coast first (earliest first), then by miles. */
	private static List<TrailParty> standings(List<TrailParty> list) {
		return list.stream().sorted(Comparator
			.comparing((TrailParty p) -> p.getArrivedTurn() == null ? Integer.MAX_VALUE : p.getArrivedTurn())
			.thenComparing(TrailParty::getMiles, Comparator.reverseOrder())).toList();
	}

	/** Places with ties: same arrival turn, or same miles on the trail. */
	private static Map<Long, Integer> places(List<TrailParty> order) {
		Map<Long, Integer> out = new HashMap<>();
		for (int i = 0; i < order.size(); i++) {
			TrailParty p = order.get(i);
			if (i > 0) {
				TrailParty prev = order.get(i - 1);
				if (Objects.equals(prev.getArrivedTurn(), p.getArrivedTurn()) && (p.getArrivedTurn() != null || prev.getMiles() == p.getMiles())) {
					out.put(p.getId(), out.get(prev.getId()));
					continue;
				}
			}
			out.put(p.getId(), i + 1);
		}
		return out;
	}

	private static String travel(TrailParty p, List<TrailPlayer> members) {
		if (p.isWagon()) return p.isWagonBroken() ? "Wagon (broken down)" : "Wagon";
		Mount slowest = members.stream().map(TrailPlayer::getMount)
			.min(Comparator.comparingInt(m -> TrailOdds.speed(m, false))).orElse(Mount.WALK);
		boolean limping = members.stream().anyMatch(TrailPlayer::isShoeThrown);
		return switch (slowest) {
			case WALK -> "Walking";
			case DONKEY -> "Donkey";
			case HORSE -> limping ? "Horse (thrown shoe)" : "Horse";
		};
	}

	private static String mountLabel(TrailPlayer pl) {
		return switch (pl.getMount()) {
			case WALK -> "On foot";
			case DONKEY -> "Donkey";
			case HORSE -> pl.isShoeThrown() ? "Horse (thrown shoe)" : "Horse";
		};
	}

	private static List<String> labels(TrailPlayer pl, int turn) {
		List<String> out = new ArrayList<>();
		if (pl.isStickyFingers()) out.add("Sticky Fingers");
		if (pl.getSamaritanUntil() >= turn && pl.getSamaritanUntil() > 0) out.add("Good Samaritan");
		return out;
	}

	// Small helpers.

	private TrailChoice choice(TrailGame g, TrailChoice.Kind kind, int offered, int expires) {
		TrailChoice c = new TrailChoice();
		c.setGameId(g.getId());
		c.setKind(kind);
		c.setOfferedTurn(offered);
		c.setExpiresTurn(expires);
		return c;
	}

	private void addLog(TrailGame g, Long partyId, Long userId, int turn, TrailLog.Kind kind, String body, Integer coins) {
		TrailLog l = new TrailLog();
		l.setGameId(g.getId());
		l.setPartyId(partyId);
		l.setUserId(userId);
		l.setTurn(turn);
		l.setKind(kind);
		l.setBody(body.length() <= 500 ? body : body.substring(0, 500));
		l.setCoins(coins);
		l.setCreatedAt(LocalDateTime.now(zone));
		logs.save(l);
	}

	private void announce(TrailGame g, String body) {
		User author = users.findById(g.getCreatedBy()).filter(User::isActive)
			.or(() -> users.findAll().stream().filter(u -> u.isActive() && u.getRoles() == Role.OWNER).findFirst())
			.orElse(null);
		if (author != null) teamBoard.announce(author, body);
	}

	private TrailPlayer me(TrailGame g, User viewer) {
		requireStaff(viewer);
		return players.findByGameIdAndUserId(g.getId(), viewer.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You're not on this trail"));
	}

	/** A game that isn't over. */
	private TrailGame open(Long id) {
		TrailGame g = find(id);
		if (g.getFinishedAt() != null) throw bad("This game is over");
		return g;
	}

	/** A game that has started and isn't over. */
	private TrailGame live(Long id) {
		TrailGame g = open(id);
		if (g.getTurn() == 0) throw bad("The game hasn't started yet");
		return g;
	}

	private TrailGame find(Long id) {
		return games.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That game isn't here anymore"));
	}

	/** On staff, not a receptionist, and linked to a Salon Ultimate name. */
	private boolean canPlay(User u) {
		return u.isActive() && u.getRoles().isStaff() && u.getRoles() != Role.RECEPTIONIST
			&& u.getSalonUltimateName() != null && !u.getSalonUltimateName().isBlank();
	}

	private LocalDate today() {
		return LocalDate.now(zone);
	}

	private String userName(User u) {
		return name(u);
	}

	private String userName(Long userId) {
		return users.findById(userId).map(TrailService::name).orElse("Former employee");
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	/** "Ava", "Ava and Mia", "Ava, Mia, and Lee". */
	private static String joinNames(List<String> names) {
		if (names.isEmpty()) return "Nobody";
		if (names.size() == 1) return names.get(0);
		if (names.size() == 2) return names.get(0) + " and " + names.get(1);
		return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.get(names.size() - 1);
	}

	private static List<Long> concat(List<Long> a, List<Long> b) {
		List<Long> out = new ArrayList<>(a);
		out.addAll(b);
		return out;
	}

	private static void requireStaff(User viewer) {
		if (!viewer.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The Grove Trail is for staff");
	}

	private static void requireManager(User viewer) {
		if (!viewer.isAdmin()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners, general managers, and managers can start a trail game");
		}
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
