package com.grove.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
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
 * The Grove Trail: stylists cross the 1800s frontier from Maple Grove to the Pacific,
 * buying provisions, animals, and wagons with coins from their Salon Ultimate service
 * sales. A new turn unlocks at every Salon Ultimate check; each party plays its turns
 * when it likes by picking one of four choices and pressing Go. Odds quietly favor
 * whoever is behind. Trading posts open only at the forts (or when a traveling trader
 * happens by), and people on the same stretch of trail sometimes run into each other:
 * to help, rob, or travel together.
 * Managers and up start games.
 */
@Service
public class TrailService {

	private static final Logger log = LoggerFactory.getLogger(TrailService.class);
	private static final String PAGE = "/trail";
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");
	private static final String PEDDLER = "A traveling trader";

	public record GameInput(String name, LocalDate startDate, String prize, Handicap handicap,
		List<Long> userIds, Integer bonusTreatment, Integer bonusColor, Integer bonusWax) {}

	public record BuyInput(Item item, Integer quantity, Boolean split) {}

	/** What Go sends: a scenario option's position, or a meeting answer ("WAVE", "STEAL_COINS"...). */
	public record PlayInput(Integer option, String answer) {}

	public record LandmarkView(String name, int miles, double lon, double lat, boolean store) {}

	public record MemberView(Long userId, String name, String mount, List<String> labels) {}

	/** {@code place} is null before the first turn. */
	public record PartyView(Long id, int miles, String at, Integer arrivedTurn, Integer place, String travel,
		boolean wagonBroken, int turnsPlayed, List<MemberView> members) {}

	public record MyMember(Long userId, String name, int coins, String mount, boolean shoeThrown) {}

	/** {@code hunger}: how bad going without food has gotten, or null when fed. */
	public record MyParty(Long id, int food, String hunger, boolean wagon, boolean wagonBroken, int spareWheels,
		int horseshoes, int carrots, int playlists, boolean puppy, List<MyMember> members) {}

	/** One of the four choices. No odds or outcomes: you find out when you press Go. */
	public record OptionView(String value, String label, boolean available) {}

	/** {@code kind}: SCENARIO, or MEETING when the turn is about someone you ran into. */
	public record TurnView(String kind, String text, List<OptionView> options) {}

	public record ShopItem(String item, String label, int price, String help) {}

	public record StoreView(String name, List<ShopItem> items) {}

	public record ChoiceOption(String value, String label) {}

	public record ChoiceView(Long id, String kind, String title, String text, List<ChoiceOption> options) {}

	public record LogView(int turn, String kind, String body, Integer coins) {}

	/** {@code ready}: a turn is unlocked and waiting. {@code lastResult}: what happened on the last turn played. */
	public record MeView(Long userId, int coins, String mount, boolean shoeThrown, int noJoinUntil, int turnsPlayed,
		boolean ready, MyParty party, TurnView turn, StoreView store, int repairPrice, List<ChoiceView> choices,
		List<String> lastResult, List<LogView> purchases) {}

	/**
	 * {@code status}: UPCOMING, LIVE, or FINISHED. {@code turn} is how many turns have unlocked.
	 * {@code practice}: the server is in practice mode (turns don't wait, 2,000 coins to start).
	 */
	public record GameView(Long id, String name, String status, boolean practice, int turn, LocalDate startDate, String prize,
		String handicap, int bonusTreatment, int bonusColor, int bonusWax, LocalDateTime lastCheckedAt, String checkProblem,
		String createdByName, int trailMiles, List<LandmarkView> landmarks, List<PartyView> parties, MeView me,
		List<LogView> log) {}

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
	/** Practice mode: turns don't wait for checks and everyone starts with more coins. Off for real games. */
	private boolean practice;
	private Random random = new Random();

	public TrailService(TrailGameRepository games, TrailPartyRepository parties, TrailPlayerRepository players,
			TrailChoiceRepository choices, TrailLogRepository logs, UserRepository users, WeeklyStatRepository stats,
			SalonUltimateClient salonUltimate, PushService push, TeamBoardService teamBoard,
			@Value("${app.time-zone:America/Chicago}") String zone, @Value("${app.trail.practice:false}") boolean practice) {
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
		this.practice = practice;
	}

	/** For tests: practice mode on or off. */
	void setPractice(boolean practice) {
		this.practice = practice;
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
			// Everyone can stock up before leaving the salon.
			p.setStoreName(TrailOdds.LANDMARKS.get(0).name());
			p = parties.save(p);
			TrailPlayer pl = new TrailPlayer();
			pl.setGameId(g.getId());
			pl.setUserId(u.getUserId());
			pl.setPartyId(p.getId());
			pl.setCoins(practice ? TrailOdds.PRACTICE_COINS : TrailOdds.STARTING_COINS);
			pl.setWorth(worths.getOrDefault(u.getUserId(), ContestService.BASE_WORTH));
			players.save(pl);
		}
		// Everyone sees their first four choices right away; a game starting today starts now, at turn 1.
		Turn first = new Turn(g);
		for (TrailParty p : first.onTrail()) deal(first, p);
		first.save();
		if (start.equals(today())) unlock(g);
		push.notify(group.stream().map(User::getUserId).toList(), name,
			"You're on the Grove Trail, starting " + (start.equals(today()) ? "now" : start.format(SHORT_DATE))
				+ ". Pack provisions.", PAGE);
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

	/** Unlock the next turn for everyone now instead of waiting for the next check. */
	public GameView unlockNow(User manager, Long id) {
		requireManager(manager);
		TrailGame g = find(id);
		if (g.getFinishedAt() != null) throw bad("This game is already over");
		if (g.getStartDate().isAfter(today())) throw bad("This game hasn't started yet");
		unlock(g);
		return view(find(id), manager);
	}

	/** A new turn at every Salon Ultimate check, the same times as contests. */
	@Scheduled(cron = "0 0 8,12,16,20 * * *", zone = "${app.time-zone:America/Chicago}")
	public void unlockAll() {
		for (TrailGame g : games.findByFinishedAtIsNullAndStartDateLessThanEqual(today())) {
			try {
				unlock(g);
			} catch (Exception e) {
				log.warn("Trail turn for game {} failed", g.getId(), e);
			}
		}
	}

	// Turns.

	/** One game's parties and players, loaded together while a turn runs. */
	private final class Turn {
		final TrailGame game;
		/** The game's clock: how many turns have unlocked. Karma and waits count in these. */
		final int t;
		/** The turn number this party is on, for its log lines. */
		int logTurn;
		final Map<Long, TrailParty> partyById = new LinkedHashMap<>();
		final List<TrailPlayer> everyone;
		final Map<Long, User> people;
		/** This turn's miles multiplier and flat miles, per party. */
		final Map<Long, Double> move = new HashMap<>();
		final Map<Long, Integer> extra = new HashMap<>();

		Turn(TrailGame game) {
			this.game = game;
			this.t = game.getTurn();
			this.logTurn = game.getTurn();
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
			return userId == null ? null : everyone.stream().filter(pl -> pl.getUserId().equals(userId)).findFirst().orElse(null);
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
			return karma + TrailOdds.paceLuck(p.getPace()) + p.getLuck() + (p.isPuppy() ? TrailOdds.PUPPY_LUCK : 0)
				+ TrailOdds.hungerLuck(p.getHungryTurns());
		}

		void story(TrailParty p, TrailLog.Kind kind, String body) {
			addLog(game, p == null ? null : p.getId(), null, logTurn, kind, body, null);
		}

		void save() {
			parties.saveAll(partyById.values());
			players.saveAll(everyone);
			games.save(game);
		}
	}

	/** Unlock the next turn: count coins and make sure everyone has something to decide. */
	synchronized void unlock(TrailGame g) {
		if (g.getFinishedAt() != null) return;
		g.setTurn(g.getTurn() + 1);
		games.save(g);
		Turn turn = new Turn(g);

		String problem = creditCoins(turn);
		g.setCheckProblem(problem == null ? null : problem.length() <= 500 ? problem : problem.substring(0, 500));
		if (problem == null) g.setLastCheckedAt(LocalDateTime.now(zone));
		expireChoices(turn);
		for (TrailParty p : turn.onTrail()) {
			if (p.getScenarioKey() == null && p.getEncounterPartyId() == null) deal(turn, p);
		}
		turn.save();
	}

	/** A manager calls it: the standings as they are now go out to everyone. */
	public synchronized GameView endNow(User manager, Long id) {
		requireManager(manager);
		TrailGame g = find(id);
		if (g.getFinishedAt() != null) throw bad("This game is already over");
		finish(new Turn(g));
		return view(find(id), manager);
	}

	/** Play the viewer's party's next turn with the option they picked. */
	public synchronized GameView play(User viewer, Long gameId, PlayInput input) {
		TrailGame g = open(gameId);
		TrailPlayer mine = me(g, viewer);
		if (g.getTurn() == 0) throw bad("The game hasn't started yet");
		Turn turn = new Turn(g);
		TrailParty p = turn.partyById.get(mine.getPartyId());
		if (p.getArrivedTurn() != null) throw bad("You've already reached the Pacific");
		if (practice && p.getTurnsPlayed() >= g.getTurn()) {
			// Practice: no waiting for the next check.
			g.setTurn(g.getTurn() + 1);
			games.save(g);
			turn = new Turn(g);
			p = turn.partyById.get(mine.getPartyId());
		}
		if (p.getTurnsPlayed() >= g.getTurn()) throw bad("Your next turn unlocks at the next Salon Ultimate check");
		playTurn(turn, p, input == null ? new PlayInput(null, null) : input, viewer.getUserId());
		turn.save();
		if (turn.onTrail().isEmpty()) finish(turn);
		return view(find(gameId), viewer);
	}

	/**
	 * One turn for one party: settle the scenario or meeting with the answer ({@code input} null
	 * means nobody answered), eat, travel, then set up the next turn. The store here closes.
	 */
	private void playTurn(Turn turn, TrailParty p, PlayInput input, Long by) {
		TrailPlayer player = turn.player(by);
		int n = p.getTurnsPlayed() + 1;
		turn.logTurn = n;
		if (p.getEncounterPartyId() != null) {
			List<OptionView> options = meetingOptions(turn, p);
			String answer = input == null || input.answer() == null ? "WAVE" : input.answer().trim().toUpperCase();
			if (options.stream().noneMatch(o -> o.value().equals(answer))) {
				if (input != null) throw bad("Pick one of the options");
			}
			settleMeeting(turn, p, options.stream().anyMatch(o -> o.value().equals(answer)) ? answer : "WAVE", player);
		} else if (p.getScenarioKey() != null) {
			Scenario sc = TrailScenarios.BY_KEY.get(p.getScenarioKey());
			Integer option = input == null ? null : input.option();
			if (sc != null && option != null) {
				if (option < 0 || option >= sc.options().size()) throw bad("Pick one of the options");
				if (!available(sc.options().get(option), p)) throw bad(unavailable(sc.options().get(option)));
			}
			if (sc != null) settleScenario(turn, p, sc, option, by);
		}
		p.setStoreName(null);
		p.setScenarioKey(null);
		p.setEncounterPartyId(null);
		p.setEncounterJoin(false);
		eat(turn, p);
		travel(turn, p, n);
		p.setTurnsPlayed(n);
		if (p.getArrivedTurn() == null) {
			next(turn, p);
			offerSkims(turn, p);
		}
	}

	/**
	 * Coins from Salon Ultimate since the game started: $1 of service sales is 1 coin, plus a
	 * bonus for each treatment, color, and wax, times each person's handicap. Only what's new
	 * since the last check is added. Returns why it couldn't be read, or null.
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

	/** Roll the scenario with the party's pick (or the safe choice) and apply what happened. */
	private void settleScenario(Turn turn, TrailParty p, Scenario sc, Integer answer, Long by) {
		List<TrailPlayer> members = turn.members(p);
		int index = answer != null ? answer : sc.defaultOption();
		if (!available(sc.options().get(index), p)) index = sc.defaultOption();
		Option op = sc.options().get(index);

		boolean good;
		String paid = "";
		if (op.cost > 0) {
			int cost = op.costEach ? op.cost * members.size() : op.cost;
			Map<TrailPlayer, Integer> taken = charge(members, cost);
			if (taken == null) {
				good = false;
				paid = " You couldn't afford it.";
			} else {
				logPurchases(turn.game, p, turn.logTurn, taken, op.label);
				good = op.sure() || roll(TrailOdds.chance(op.base, turn.behind(p), turn.bonus(p)));
			}
		} else {
			good = op.sure() || roll(TrailOdds.chance(op.base, turn.behind(p), turn.bonus(p)));
		}
		p.setLuck(0);
		Fx fx = good ? op.good : op.bad;
		String effects = apply(turn, p, members, fx);
		// Just what happened: that's what the player sees after Go, then how far they got.
		String result = (paid + " " + fx.text + effects).trim();
		if (!result.isEmpty()) turn.story(p, TrailLog.Kind.STORY, result);
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
		push.notify(turn.ids(p), turn.game.getName(), "Your wagon broke down. It won't move until someone pays for a repair.", PAGE);
		return " The wagon is broken down until it's repaired.";
	}

	/**
	 * Everyone eats. Without enough, it gets worse every turn: hungry (half speed), weak (worse luck),
	 * starving (a quarter speed, much worse luck), then too weak to travel at all until there's food.
	 */
	private void eat(Turn turn, TrailParty p) {
		int need = turn.members(p).size() * TrailOdds.paceFood(p.getPace()) + (p.isPuppy() ? 1 : 0);
		if (p.getFood() >= need) {
			p.setFood(p.getFood() - need);
			p.setHangry(false);
			return;
		}
		p.setFood(0);
		p.setHangry(true);
		p.setHungryTurns(p.getHungryTurns() + 1);
		int n = p.getHungryTurns();
		String text = switch (n) {
			case 1 -> "Out of provisions. Everyone is hungry: half speed until you find food.";
			case 2 -> "A second turn without food. Everyone is weak, and luck is turning against you.";
			case 4 -> "Starving. A quarter speed now, and everything is harder.";
			default -> n >= 6 ? "Too weak to travel. You lie by the trail and hope for a trader, a hunt, or a kind stranger." : null;
		};
		if (text != null) turn.story(p, TrailLog.Kind.STORY, text);
		if (n == 4 || n == 6) push.notify(turn.ids(p), turn.game.getName(), text, PAGE);
	}

	/** Move west; passing a fort opens its trading post, and now and then a traveling trader turns up. */
	private void travel(Turn turn, TrailParty p, int n) {
		List<TrailPlayer> members = turn.members(p);
		fixShoes(p, members);
		int before = p.getMiles();
		int base = p.isWagon() ? TrailOdds.WAGON_SPEED
			: members.stream().mapToInt(pl -> TrailOdds.speed(pl.getMount(), pl.isShoeThrown())).min().orElse(TrailOdds.WALK_SPEED);
		if (!p.isWagonBroken() && TrailOdds.hungerSpeed(p.getHungryTurns()) > 0) {
			double miles = base * TrailOdds.paceSpeed(p.getPace())
				* TrailOdds.hungerSpeed(p.getHungryTurns()) * turn.move.getOrDefault(p.getId(), 1.0)
				+ turn.extra.getOrDefault(p.getId(), 0);
			p.setMiles(Math.max(0, Math.min(TrailOdds.TRAIL_MILES, before + (int) Math.round(miles))));
		}
		// Every turn says, in real miles, how far you got.
		turn.story(p, TrailLog.Kind.STORY, distance(p.getMiles() - before, (int) Math.round(base * TrailOdds.paceSpeed(p.getPace()))));
		if (p.isWagon() && !p.isWagonBroken() && p.getMiles() > before) {
			double chance = TrailOdds.WAGON_BREAKDOWN * (p.getPace() == Pace.GRUELING ? 2 : 1);
			if (random.nextDouble() < chance) turn.story(p, TrailLog.Kind.STORY, "A wheel falls off for no reason at all." + breakDown(turn, p));
		}

		int from = TrailOdds.landmarkAt(before);
		int to = TrailOdds.landmarkAt(p.getMiles());
		boolean coast = p.getMiles() >= TrailOdds.TRAIL_MILES;
		for (int i = to; i > from && !coast; i--) {
			if (TrailOdds.LANDMARKS.get(i).store()) {
				p.setStoreName(TrailOdds.LANDMARKS.get(i).name());
				break;
			}
		}
		if (p.getStoreName() == null && !coast && random.nextDouble() < TrailOdds.PEDDLER) p.setStoreName(PEDDLER);
		if (to <= from) return;

		String place = TrailOdds.LANDMARKS.get(to).name();
		if (coast) p.setArrivedTurn(n);
		turn.story(p, TrailLog.Kind.LANDMARK, (coast ? "You made it to the Pacific! " : "You reached ") + place + ".");
		// Only the coast gets a notification of its own; there's a landmark every couple of turns.
		if (coast) {
			push.notify(turn.ids(p), turn.game.getName(),
				"You reached the Pacific. Enjoy Haystack Rock while the others catch up.", PAGE);
		}
		if (coast || to > turn.game.getFurthestLandmark()) {
			boolean first = to > turn.game.getFurthestLandmark();
			turn.game.setFurthestLandmark(Math.max(turn.game.getFurthestLandmark(), to));
			String text = coast
				? turn.names(p) + " reached the Pacific" + (first ? ", first of everyone" : "") + "!"
				: turn.names(p) + " reached " + place + " first.";
			addLog(turn.game, null, null, turn.t, TrailLog.Kind.LANDMARK, text, null);
			announce(turn.game, turn.game.getName() + ": " + text);
		}
	}

	/** How far the party got this turn, against how far it usually goes. */
	static String distance(int moved, int usual) {
		if (moved < 0) return "You lose " + -moved + (moved == -1 ? " mile" : " miles") + " of ground.";
		if (moved == 0) return "You don't get anywhere.";
		if (moved < usual * 0.9) return "Slow going: you cover " + moved + (moved == 1 ? " mile." : " miles.");
		return "You cover " + moved + " miles.";
	}

	/** The next turn: sometimes running into someone on the same stretch of trail, otherwise a scenario. */
	private void next(Turn turn, TrailParty p) {
		int stretch = TrailOdds.landmarkAt(p.getMiles());
		List<TrailParty> near = p.getMiles() == 0 ? List.of() : turn.onTrail().stream()
			.filter(o -> !o.getId().equals(p.getId()) && o.getMiles() > 0 && TrailOdds.landmarkAt(o.getMiles()) == stretch)
			.filter(o -> !recentlyMet(turn, p, o))
			.toList();
		if (!near.isEmpty() && random.nextDouble() < TrailOdds.MEET_CHANCE) {
			TrailParty o = near.stream().min(Comparator.comparingInt(x -> Math.abs(x.getMiles() - p.getMiles()))).orElseThrow();
			p.setEncounterPartyId(o.getId());
			p.setEncounterJoin(canTravelTogether(turn, p, o) && random.nextDouble() < TrailOdds.JOIN_OFFER);
			TrailChoice met = choice(turn.game, TrailChoice.Kind.MEET, turn.t, turn.t);
			met.setPartyId(p.getId());
			met.setOtherPartyId(o.getId());
			met.setResolved(true);
			choices.save(met);
			return;
		}
		deal(turn, p);
	}

	private boolean recentlyMet(Turn turn, TrailParty a, TrailParty b) {
		return choices.findByGameId(turn.game.getId()).stream().anyMatch(c -> c.getKind() == TrailChoice.Kind.MEET
			&& ((a.getId().equals(c.getPartyId()) && b.getId().equals(c.getOtherPartyId()))
				|| (b.getId().equals(c.getPartyId()) && a.getId().equals(c.getOtherPartyId())))
			&& turn.t - c.getOfferedTurn() < TrailOdds.MEET_AGAIN_TURNS);
	}

	/** One of them travels alone, there's room, and nobody needs space. */
	private boolean canTravelTogether(Turn turn, TrailParty a, TrailParty b) {
		List<TrailPlayer> ma = turn.members(a), mb = turn.members(b);
		if (ma.size() > 1 && mb.size() > 1) return false;
		if (ma.size() + mb.size() > TrailOdds.MAX_PARTY) return false;
		return ma.stream().allMatch(pl -> pl.getNoJoinUntil() <= turn.t) && mb.stream().allMatch(pl -> pl.getNoJoinUntil() <= turn.t);
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
		// Only scenarios where all four choices work right now: no selling provisions you don't have.
		List<Scenario> usable = TrailScenarios.ALL.stream()
			.filter(s -> fits.contains(s.who()) && s.options().stream().allMatch(o -> available(o, p)))
			.toList();
		List<Scenario> options = usable.stream().filter(s -> !seen.contains(s.key())).toList();
		if (options.isEmpty()) {
			seen.clear();
			options = usable.isEmpty() ? TrailScenarios.ALL.stream().filter(s -> s.who() == Who.ANY).toList() : usable;
		}
		Scenario sc = options.get(random.nextInt(options.size()));
		seen.add(sc.key());
		p.setScenarioKey(sc.key());
		p.setEncounterPartyId(null);
		p.setSeen(String.join(",", seen));
	}

	private void finish(Turn turn) {
		TrailGame g = turn.game;
		g.setFinishedAt(LocalDateTime.now(zone));
		games.save(g);
		List<TrailParty> order = standings(turn.partyById.values().stream().filter(p -> !turn.members(p).isEmpty()).toList());
		Map<Long, Integer> places = places(order);
		List<String> lines = new ArrayList<>();
		lines.add(g.getName() + " is over. Final standings:");
		for (TrailParty p : order) {
			String where = p.getArrivedTurn() != null ? "reached the Pacific"
				: "mile " + p.getMiles() + ", near " + TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(p.getMiles())).name();
			lines.add(ContestService.ordinal(places.get(p.getId())) + ": " + turn.names(p) + ", " + where);
		}
		int winnerCount = order.stream().filter(p -> places.get(p.getId()) == 1).mapToInt(p -> turn.members(p).size()).sum();
		if (g.getPrize() != null) {
			lines.add("");
			lines.add("Prize: " + g.getPrize() + (winnerCount > 1 ? ", split " + winnerCount + " ways." : "."));
		}
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.LANDMARK, String.join(" ", lines.subList(0, Math.min(lines.size(), 4))), null);
		announce(g, String.join("\n", lines));
		for (TrailParty p : order) {
			push.notify(turn.ids(p), g.getName() + " is over",
				"You finished " + ContestService.ordinal(places.get(p.getId())) + (p.getArrivedTurn() != null ? ", at the Pacific." : ", at mile " + p.getMiles() + "."),
				PAGE);
		}
	}

	// Meetings: helping, robbing, or traveling together.

	/**
	 * The four things a party can do about someone it ran into, first four that make sense of: ride on
	 * (the default), share provisions (if you have any), rob them, travel together (when on offer),
	 * help mend their broken wagon, lend coin (if you have any), raid their provisions.
	 */
	private List<OptionView> meetingOptions(Turn turn, TrailParty p) {
		TrailParty them = p.getEncounterPartyId() == null ? null : turn.partyById.get(p.getEncounterPartyId());
		boolean coins = turn.members(p).stream().anyMatch(pl -> pl.getCoins() > 0);
		List<OptionView> out = new ArrayList<>();
		out.add(new OptionView("WAVE", "Tip your hat and ride on", true));
		if (p.getFood() > 0) out.add(new OptionView("HELP_FOOD", "Share your provisions", true));
		out.add(new OptionView("STEAL_COINS", "Rob them in the night", true));
		if (them != null && p.isEncounterJoin() && canTravelTogether(turn, p, them)) out.add(new OptionView("JOIN", "Ask them to travel together", true));
		if (them != null && them.isWagonBroken()) out.add(new OptionView("HELP_WAGON", "Help mend their wagon", true));
		if (coins) out.add(new OptionView("HELP_COINS", "Lend them some coin", true));
		out.add(new OptionView("STEAL_FOOD", "Raid their provisions", true));
		return out.subList(0, Math.min(4, out.size()));
	}

	private void settleMeeting(Turn turn, TrailParty p, String answer, TrailPlayer me) {
		TrailParty them = turn.partyById.get(p.getEncounterPartyId());
		if (them == null || turn.members(them).isEmpty() || them.getArrivedTurn() != null) {
			turn.story(p, TrailLog.Kind.STORY, "By the time you broke camp, they'd moved on.");
			return;
		}
		switch (answer) {
			case "JOIN" -> {
				TrailChoice c = choice(turn.game, TrailChoice.Kind.INVITE, turn.t, turn.t + TrailOdds.INVITE_TURNS);
				c.setPartyId(them.getId());
				c.setOtherPartyId(p.getId());
				c.setOtherUserId(me == null ? null : me.getUserId());
				choices.save(c);
				turn.story(p, TrailLog.Kind.PARTY, "You asked " + turn.names(them) + " to travel together. They'll answer on their turn.");
				push.notify(turn.ids(them), turn.game.getName(), turn.names(p) + " asked you to travel together.", PAGE);
			}
			case "HELP_FOOD", "HELP_COINS", "HELP_WAGON" -> help(turn, me, p, them, answer);
			case "STEAL_COINS", "STEAL_FOOD" -> steal(turn, me, p, them, answer);
			default -> turn.story(p, TrailLog.Kind.STORY, "You tipped your hat to " + turn.names(them) + ". They tipped theirs.");
		}
	}

	private void help(Turn turn, TrailPlayer helper, TrailParty giver, TrailParty taker, String how) {
		String helperName = helper == null ? turn.names(giver) : turn.nameOf(helper.getUserId());
		String gave = null;
		switch (how) {
			case "HELP_FOOD" -> {
				int n = Math.min(TrailOdds.HELP_FOOD_MAX, giver.getFood());
				if (n > 0) {
					giver.setFood(giver.getFood() - n);
					taker.setFood(taker.getFood() + n);
					taker.setHangry(false);
					gave = n + (n == 1 ? " provision" : " provisions");
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
			default -> {
				if (taker.isWagonBroken()) {
					int cost = (int) Math.round(turn.price(Item.REPAIR) * TrailOdds.HELP_REPAIR_SHARE);
					Map<TrailPlayer, Integer> paid = charge(turn.members(giver), cost);
					if (paid != null) {
						logPurchases(turn.game, giver, turn.logTurn, paid, "fixing " + turn.names(taker) + "'s wagon");
						taker.setWagonBroken(false);
						gave = "a wagon repair";
					}
				}
			}
		}
		if (gave == null) {
			turn.story(giver, TrailLog.Kind.HELP, "You wanted to help " + turn.names(taker) + " but had nothing to give. The thought counts, a little.");
			return;
		}
		if (helper != null) {
			addKarma(helper, TrailOdds.HELP_KARMA, turn.t);
			helper.setSamaritanUntil(turn.t + TrailOdds.SAMARITAN_TURNS);
		}
		giver.setLuck(Math.max(giver.getLuck(), TrailOdds.HELP_KARMA));
		turn.story(giver, TrailLog.Kind.HELP, "You gave " + turn.names(taker) + " " + gave + ". The trail remembers kindness.");
		addLog(turn.game, null, null, turn.t, TrailLog.Kind.HELP, helperName + " gave " + turn.names(taker) + " " + gave + ". A Good Samaritan.", null);
		push.notify(turn.ids(taker), turn.game.getName(), helperName + " gave you " + gave + ".", PAGE);
	}

	private void steal(Turn turn, TrailPlayer thief, TrailParty thieves, TrailParty target, String what) {
		if (thief == null) return;
		String thiefName = turn.nameOf(thief.getUserId());
		addKarma(thief, TrailOdds.THEFT_KARMA, turn.t);
		boolean topThird = topThird(turn, thieves);
		List<TrailPlayer> victims = turn.members(target);

		int coins = 0, food = 0;
		if (what.equals("STEAL_COINS")) {
			int pool = victims.stream().mapToInt(TrailPlayer::getCoins).sum();
			coins = Math.min(TrailOdds.STEAL_COIN_MAX, (int) Math.round(pool * TrailOdds.STEAL_COIN_SHARE));
			if (topThird) coins /= 2;
		} else {
			food = Math.min(TrailOdds.STEAL_FOOD_MAX, target.getFood());
			if (topThird) food /= 2;
		}
		String haul = coins > 0 ? coins + " coins" : food > 0 ? food + (food == 1 ? " provision" : " provisions") : null;
		if (haul == null) {
			turn.story(thieves, TrailLog.Kind.THEFT, "You crept into their camp, but they had nothing worth taking.");
			addLog(turn.game, null, null, turn.t, TrailLog.Kind.THEFT,
				thiefName + " tried to rob " + turn.names(target) + ", but they had nothing worth taking.", null);
			return;
		}
		int haulValue = coins + food * turn.price(Item.FOOD);
		int chance = TrailOdds.stealChance(worth(turn, thieves), worth(turn, target), target.isWagonBroken());
		if (roll(chance)) {
			if (coins > 0) give(List.of(thief), sum(take(victims, coins)));
			if (food > 0) {
				target.setFood(target.getFood() - food);
				thieves.setFood(thieves.getFood() + food);
			}
			turn.story(thieves, TrailLog.Kind.THEFT, "You got away with " + haul + ".");
			String text = thiefName + " robbed " + turn.names(target) + " of " + haul + (topThird ? " (and felt a little bad about it)" : "") + ".";
			addLog(turn.game, null, null, turn.t, TrailLog.Kind.THEFT, text, null);
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
		turn.story(thieves, TrailLog.Kind.THEFT, "Caught! " + (fine > 0 ? "You paid them " + fine + " coins and lost" : "You lost")
			+ " the day explaining yourself to a territorial marshal.");
		String text = thiefName + " tried to rob " + turn.names(target) + " of " + haul + " and got caught. "
			+ (fine > 0 ? "Paid them " + fine + " coins and " : "") + "lost the day explaining themselves to a territorial marshal.";
		addLog(turn.game, null, null, turn.t, TrailLog.Kind.THEFT, text, null);
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

	// Skimming from your own party.

	/** Now and then, someone in a party gets a private chance to skim. */
	private void offerSkims(Turn turn, TrailParty p) {
		List<TrailPlayer> members = turn.members(p);
		if (members.size() < 2) return;
		for (TrailPlayer pl : members) {
			if (turn.t < TrailOdds.SKIM_FIRST_TURN || turn.t - pl.getLastSkimTurn() < TrailOdds.SKIM_EVERY
				|| turn.t - pl.getJoinedTurn() < TrailOdds.SKIM_AFTER_JOINING) continue;
			if (random.nextDouble() >= TrailOdds.SKIM_OFFER) continue;
			pl.setLastSkimTurn(turn.t);
			TrailChoice c = choice(turn.game, TrailChoice.Kind.SKIM, turn.t, turn.t + 1);
			c.setUserId(pl.getUserId());
			c.setPartyId(p.getId());
			choices.save(c);
			push.notify(List.of(pl.getUserId()), turn.game.getName(), "Psst. Nobody is watching the party's coins right now.", PAGE);
		}
	}

	/** Skims, requests, get-evens, and votes nobody answered in time. */
	private void expireChoices(Turn turn) {
		for (TrailChoice c : choices.findByGameIdAndResolvedFalse(turn.game.getId())) {
			if (c.getExpiresTurn() >= turn.t) continue;
			c.setResolved(true);
			choices.save(c);
		}
	}

	// What players do between turns.

	@Transactional
	public GameView buy(User viewer, Long gameId, BuyInput input) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty p = parties.findById(me.getPartyId()).orElseThrow();
		if (p.getArrivedTurn() != null) throw bad("You've reached the Pacific. Nothing to buy here but salt water.");
		if (input == null || input.item() == null) throw bad("Pick something to buy");
		Item item = input.item();
		// A wheelwright can be sent for anywhere; everything else needs a trading post.
		if (item != Item.REPAIR && p.getStoreName() == null) throw bad("There's no trading post here. Keep going and you'll find one.");
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
		logPurchases(g, p, p.getTurnsPlayed(), paid, (quantity > 1 ? quantity + " x " : "") + item.label);
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
		addLog(g, p.getId(), null, p.getTurnsPlayed(), TrailLog.Kind.PARTY,
			userName(viewer) + " left the wagon on the prairie. Someone will make it a chicken coop.", null);
		return view(g, viewer);
	}

	/** Answer a request to travel together, a chance to skim, getting even, or a vote. */
	@Transactional
	public GameView answer(User viewer, Long gameId, Long choiceId, String answer) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailChoice c = choices.findById(choiceId).filter(x -> x.getGameId().equals(gameId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That's not a question anymore"));
		boolean mine = c.getKind() == TrailChoice.Kind.INVITE ? me.getPartyId().equals(c.getPartyId()) : me.getUserId().equals(c.getUserId());
		if (!mine || c.getKind() == TrailChoice.Kind.MEET) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That question isn't yours");
		if (c.isResolved() || c.getExpiresTurn() < g.getTurn()) throw bad("Too late. That moment has passed.");
		String a = answer == null ? "" : answer.trim().toUpperCase();
		switch (c.getKind()) {
			case INVITE -> answerInvite(g, me, c, a);
			case SKIM -> skim(g, me, c, a);
			case GET_EVEN -> getEven(g, me, c, a);
			case VOTE_OUT -> voteOut(g, me, c, a);
			default -> { }
		}
		return view(g, viewer);
	}

	/** Saying yes merges the two parties: whoever travels alone joins the other, at the rearmost spot. */
	private void answerInvite(TrailGame g, TrailPlayer me, TrailChoice c, String a) {
		if (!a.equals("ACCEPT") && !a.equals("DECLINE")) throw bad("Yes or no");
		c.setAnswer(a);
		c.setAnsweredBy(me.getUserId());
		c.setResolved(true);
		choices.save(c);
		TrailParty mine = parties.findById(me.getPartyId()).orElseThrow();
		TrailParty asker = parties.findById(c.getOtherPartyId()).orElse(null);
		List<TrailPlayer> askers = asker == null ? List.of() : players.findByPartyId(asker.getId());
		String myName = userName(me.getUserId());
		if (a.equals("DECLINE")) {
			push.notify(askers.stream().map(TrailPlayer::getUserId).toList(), g.getName(), myName + " would rather go it alone. For now.", PAGE);
			return;
		}
		if (asker == null || askers.isEmpty()) throw bad("They're not traveling anymore");
		if (asker.getArrivedTurn() != null || mine.getArrivedTurn() != null) throw bad("Nobody forms parties at the Pacific");
		if (TrailOdds.landmarkAt(asker.getMiles()) != TrailOdds.landmarkAt(mine.getMiles())) {
			throw bad("You've drifted apart. You'd have to be on the same stretch of trail.");
		}
		Turn turn = new Turn(g);
		TrailParty a1 = turn.partyById.get(mine.getId()), a2 = turn.partyById.get(asker.getId());
		if (!canTravelTogether(turn, a1, a2)) throw bad("You can't travel together right now");
		// The one traveling alone joins the other; when both are, the one who said yes joins the one who asked.
		TrailParty into = turn.members(a2).size() > 1 || turn.members(a1).size() == 1 ? a2 : a1;
		TrailParty from = into == a2 ? a1 : a2;
		TrailPlayer joiner = turn.members(from).get(0);
		String note = "";
		into.setMiles(Math.min(into.getMiles(), from.getMiles()));
		into.setTurnsPlayed(Math.max(into.getTurnsPlayed(), from.getTurnsPlayed()));
		into.setFood(into.getFood() + from.getFood());
		into.setSpareWheels(into.getSpareWheels() + from.getSpareWheels());
		into.setHorseshoes(into.getHorseshoes() + from.getHorseshoes());
		into.setCarrots(into.getCarrots() + from.getCarrots());
		into.setPlaylists(into.getPlaylists() + from.getPlaylists());
		into.setPuppy(into.isPuppy() || from.isPuppy());
		if (into.getStoreName() == null) into.setStoreName(from.getStoreName());
		if (from.isWagon() && into.isWagon()) {
			int back = (int) Math.round(TrailOdds.price(Item.WAGON, g.getPriceScale()) * TrailOdds.WAGON_RESALE);
			joiner.setCoins(joiner.getCoins() + back);
			note = " One wagon too many: " + turn.nameOf(joiner.getUserId()) + " sold theirs back for " + back + " coins.";
		} else if (from.isWagon()) {
			into.setWagon(true);
			into.setWagonBroken(from.isWagonBroken());
		}
		if (into.getFood() > 0) into.setHangry(false);
		useSpareWheel(into);
		if (into.getScenarioKey() == null && into.getEncounterPartyId() == null) deal(turn, into);
		joiner.setPartyId(into.getId());
		joiner.setJoinedTurn(g.getTurn());
		parties.save(into);
		players.save(joiner);
		for (TrailChoice other : choices.findByGameIdAndResolvedFalse(g.getId())) {
			if (from.getId().equals(other.getPartyId()) || from.getId().equals(other.getOtherPartyId())) {
				other.setResolved(true);
				choices.save(other);
			}
		}
		for (TrailParty p : turn.partyById.values()) {
			if (from.getId().equals(p.getEncounterPartyId())) {
				p.setEncounterPartyId(null);
				p.setEncounterJoin(false);
				deal(turn, p);
				parties.save(p);
			}
		}
		parties.delete(from);
		String joinerName = turn.nameOf(joiner.getUserId());
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.PARTY, joinerName + " is now traveling with "
			+ joinNames(turn.members(into).stream().filter(pl -> pl != joiner).map(pl -> turn.nameOf(pl.getUserId())).toList())
			+ ", from mile " + into.getMiles() + "." + note, null);
		push.notify(turn.ids(into).stream().filter(id -> !id.equals(me.getUserId())).toList(),
			g.getName(), myName + " said yes. You're traveling together now.", PAGE);
	}

	/** Leave your party: keep your coins and your animal, take your share of the food, and need some space. */
	@Transactional
	public GameView leave(User viewer, Long gameId) {
		TrailGame g = open(gameId);
		TrailPlayer me = me(g, viewer);
		TrailParty party = parties.findById(me.getPartyId()).orElseThrow();
		List<TrailPlayer> members = players.findByPartyId(party.getId());
		if (members.size() < 2) throw bad("You're already on your own");
		if (party.getArrivedTurn() != null) throw bad("You've reached the Pacific. There's nowhere left to go.");
		int share = party.getFood() / members.size();
		split(g, me, party, share, TrailOdds.SPACE_TURNS);
		addLog(g, null, null, g.getTurn(), TrailLog.Kind.PARTY, userName(viewer) + " left their party at mile " + party.getMiles()
			+ " with " + share + (share == 1 ? " provision" : " provisions") + ". It's not you, it's the trail.", null);
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
		alone.setTurnsPlayed(from.getTurnsPlayed());
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
		if (g.getFinishedAt() == null && g.getTurn() > 0) {
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
				if (n <= 0) throw bad("There are no provisions to take");
				party.setFood(party.getFood() - n);
				value = n * TrailOdds.price(Item.FOOD, scale);
				me.setCoins(me.getCoins() + value);
				what = n + (n == 1 ? " provision" : " provisions") + " (sold to a passing freighter for " + value + " coins)";
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
			text = "Someone took " + what + " from the party. The jerky is also missing.";
		}
		parties.save(party);
		players.saveAll(victims);
		players.save(me);
		addLog(g, party.getId(), null, party.getTurnsPlayed(), TrailLog.Kind.THEFT, text, null);
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
		int partyTurn = parties.findById(c.getPartyId()).map(TrailParty::getTurnsPlayed).orElse(g.getTurn());
		addLog(g, c.getPartyId(), null, partyTurn, TrailLog.Kind.THEFT, text, null);
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

	private static boolean available(Option op, TrailParty p) {
		if (p.getFood() < op.foodNeeded) return false;
		return switch (op.needs) {
			case NOTHING -> true;
			case WAGON -> p.isWagon();
			case CARROTS -> p.getCarrots() > 0;
			case PLAYLIST -> p.getPlaylists() > 0;
		};
	}

	private static String unavailable(Option op) {
		return op.foodNeeded > 0 ? "You don't have the provisions for that" : op.needs == Needs.WAGON ? "You need a wagon for that" : op.needs == Needs.CARROTS ? "You don't have any carrots"
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
				p.getArrivedTurn(), g.getTurn() > 0 ? places.get(p.getId()) : null, travel(p, members), p.isWagonBroken(),
				p.getTurnsPlayed(), mv);
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
		List<LandmarkView> landmarks = TrailOdds.LANDMARKS.stream()
			.map(l -> new LandmarkView(l.name(), l.miles(), l.lon(), l.lat(), l.store())).toList();
		String status = g.getFinishedAt() != null ? "FINISHED" : g.getTurn() == 0 ? "UPCOMING" : "LIVE";
		return new GameView(g.getId(), g.getName(), status, practice, g.getTurn(), g.getStartDate(), g.getPrize(),
			g.getHandicap().name(), g.getBonusTreatment(), g.getBonusColor(), g.getBonusWax(), g.getLastCheckedAt(),
			viewer.isAdmin() ? g.getCheckProblem() : null,
			users.findById(g.getCreatedBy()).map(TrailService::name).orElse("Former employee"),
			TrailOdds.TRAIL_MILES, landmarks, partyViews, meView, story);
	}

	private MeView me(Turn turn, TrailPlayer me) {
		TrailGame g = turn.game;
		TrailParty p = turn.partyById.get(me.getPartyId());
		List<TrailPlayer> members = turn.members(p);
		MyParty party = new MyParty(p.getId(), p.getFood(), TrailOdds.hungerLabel(p.getHungryTurns()), p.isWagon(), p.isWagonBroken(),
			p.getSpareWheels(), p.getHorseshoes(), p.getCarrots(), p.getPlaylists(), p.isPuppy(),
			members.stream().map(pl -> new MyMember(pl.getUserId(), turn.nameOf(pl.getUserId()), pl.getCoins(), pl.getMount().name(), pl.isShoeThrown()))
				.sorted(Comparator.comparing(m -> m.name().toLowerCase())).toList());
		boolean ready = g.getFinishedAt() == null && p.getArrivedTurn() == null
			&& (practice ? g.getTurn() > 0 : p.getTurnsPlayed() < g.getTurn());

		TurnView next = null;
		if (p.getArrivedTurn() == null && g.getFinishedAt() == null) {
			if (p.getEncounterPartyId() != null) {
				TrailParty them = turn.partyById.get(p.getEncounterPartyId());
				String who = them == null ? "someone" : turn.names(them);
				String where = TrailOdds.LANDMARKS.get(TrailOdds.landmarkAt(p.getMiles())).name();
				next = new TurnView("MEETING", "You come upon " + who + "'s camp near " + where
					+ (them != null && them.isWagonBroken() ? ". Their wagon is broken down." : ".") + " What do you do?",
					meetingOptions(turn, p));
			} else if (p.getScenarioKey() != null && TrailScenarios.BY_KEY.containsKey(p.getScenarioKey())) {
				Scenario sc = TrailScenarios.BY_KEY.get(p.getScenarioKey());
				List<OptionView> options = new ArrayList<>();
				for (int i = 0; i < sc.options().size(); i++) {
					Option op = sc.options().get(i);
					options.add(new OptionView(String.valueOf(i), op.label, available(op, p)));
				}
				next = new TurnView("SCENARIO", sc.text(), options);
			}
		}

		StoreView store = null;
		if (p.getStoreName() != null && p.getArrivedTurn() == null && g.getFinishedAt() == null) {
			store = new StoreView(p.getStoreName(), Arrays.stream(Item.values()).filter(i -> i != Item.REPAIR)
				.map(i -> new ShopItem(i.name(), i.label, turn.price(i), i.help)).toList());
		}

		List<ChoiceView> mine = new ArrayList<>();
		for (TrailChoice c : choices.findByGameIdAndResolvedFalse(g.getId())) {
			if (c.getExpiresTurn() < g.getTurn() || c.getKind() == TrailChoice.Kind.MEET) continue;
			boolean forMe = c.getKind() == TrailChoice.Kind.INVITE ? p.getId().equals(c.getPartyId()) : me.getUserId().equals(c.getUserId());
			if (forMe) mine.add(choiceView(turn, c, p, me));
		}

		List<String> lastResult = logs.findByGameIdOrderByIdDesc(g.getId()).stream()
			.filter(l -> p.getId().equals(l.getPartyId()) && l.getKind() != TrailLog.Kind.PURCHASE && l.getTurn() == p.getTurnsPlayed()
				&& p.getTurnsPlayed() > 0)
			.sorted(Comparator.comparing(TrailLog::getId))
			.map(TrailLog::getBody)
			.toList();
		List<LogView> purchases = logs.findByGameIdOrderByIdDesc(g.getId()).stream()
			.filter(l -> l.getKind() == TrailLog.Kind.PURCHASE && p.getId().equals(l.getPartyId()))
			.limit(40)
			.map(l -> new LogView(l.getTurn(), l.getKind().name(), l.getBody(), l.getCoins()))
			.toList();
		return new MeView(me.getUserId(), me.getCoins(), me.getMount().name(), me.isShoeThrown(), me.getNoJoinUntil(),
			p.getTurnsPlayed(), ready, party, next, store, turn.price(Item.REPAIR), mine, lastResult, purchases);
	}

	private ChoiceView choiceView(Turn turn, TrailChoice c, TrailParty p, TrailPlayer me) {
		return switch (c.getKind()) {
			case INVITE -> new ChoiceView(c.getId(), "INVITE",
				turn.nameOf(c.getOtherUserId()) + " wants to travel together",
				"You'd travel as one party from wherever the rearmost of you is. Food is shared and purchases can be split, but you eat more, bad luck hits everyone, and the prize is split.",
				List.of(new ChoiceOption("ACCEPT", "Travel together"), new ChoiceOption("DECLINE", "No thanks")));
			case SKIM -> {
				List<ChoiceOption> opts = new ArrayList<>();
				int pool = turn.members(p).stream().filter(pl -> !pl.getId().equals(me.getId())).mapToInt(TrailPlayer::getCoins).sum();
				int coins = Math.min(TrailOdds.SKIM_COIN_MAX, (int) Math.round(pool * TrailOdds.SKIM_COIN_SHARE));
				if (coins > 0) opts.add(new ChoiceOption("COINS", "Pocket " + coins + " coins from the others"));
				if (p.getFood() > 0) opts.add(new ChoiceOption("FOOD", "Sell " + Math.min(TrailOdds.SKIM_FOOD_MAX, p.getFood()) + " provisions to a passing freighter"));
				if (p.getSpareWheels() > 0) opts.add(new ChoiceOption("PARTS", "Sell the spare wheel"));
				else if (p.getHorseshoes() > 0) opts.add(new ChoiceOption("PARTS", "Sell the horseshoes"));
				opts.add(new ChoiceOption("NO", "Not today"));
				yield new ChoiceView(c.getId(), "SKIM", "Skim off the top?",
					"Nobody is watching the party's stuff right now. Only you can see this. If you're caught, everyone will know.", opts);
			}
			case GET_EVEN -> new ChoiceView(c.getId(), "GET_EVEN", "Get even?",
				"Someone took from you. You could take back double, if it works. If it doesn't, you're the one with Sticky Fingers.",
				List.of(new ChoiceOption("YES", "Get even"), new ChoiceOption("NO", "Let it go")));
			case VOTE_OUT -> new ChoiceView(c.getId(), "VOTE_OUT", "Vote " + turn.nameOf(c.getOtherUserId()) + " out of the party?",
				"They'd leave with no food and couldn't travel with anyone for " + TrailOdds.SPACE_TURNS * 2 + " turns.",
				List.of(new ChoiceOption("YES", "Vote them out"), new ChoiceOption("NO", "Give them another chance")));
			default -> null;
		};
	}

	/** At the coast first (fewest turns first), then by miles. */
	private static List<TrailParty> standings(List<TrailParty> list) {
		return list.stream().sorted(Comparator
			.comparing((TrailParty p) -> p.getArrivedTurn() == null ? Integer.MAX_VALUE : p.getArrivedTurn())
			.thenComparing(TrailParty::getMiles, Comparator.reverseOrder())).toList();
	}

	/** Places with ties: same number of turns to the coast, or same miles on the trail. */
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
		return userId == null ? "Someone" : users.findById(userId).map(TrailService::name).orElse("Former employee");
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
