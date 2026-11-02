package com.grove.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.grove.Domain.Contest;
import com.grove.Domain.Contest.Metric;
import com.grove.Domain.ContestEntry;
import com.grove.Domain.ContestTeam;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.ContestEntryRepository;
import com.grove.Repository.ContestRepository;
import com.grove.Repository.ContestTeamRepository;
import com.grove.Repository.UserRepository;

/**
 * Contests: who can do the most treatments, colors, or waxes between two dates,
 * counted from Salon Ultimate, optionally in teams. Every few hours during the day
 * the standings are re-read; people whose place changed get a notification, and so
 * does everyone when a different team takes the lead. After the last day the final
 * standings go to everyone in it and onto the team board. Managers and up run them.
 */
@Service
public class ContestService {

	private static final Logger log = LoggerFactory.getLogger(ContestService.class);
	private static final int MAX_DAYS = 60;
	private static final int MAX_TEAMS = 4;
	private static final String PAGE = "/contests";
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");

	public record PersonInput(Long userId, Integer team) {}

	/** What the contest form sends. {@code teams} is empty for everyone-for-themselves; otherwise each person's {@code team} is a position in it. */
	public record ContestInput(String name, Metric metric, LocalDate startDate, LocalDate endDate, String prize,
		List<String> teams, List<PersonInput> people) {}

	public record TeamView(int position, String name, int total, Integer rank, int members) {}

	public record StandingView(Long userId, String name, Integer count, Integer rank, Integer team) {}

	/** {@code status}: UPCOMING, LIVE, ENDED (waiting for the final check), or FINISHED. */
	public record ContestView(Long id, String name, String metric, String metricLabel, LocalDate startDate, LocalDate endDate,
		String prize, String status, LocalDateTime lastCheckedAt, String checkProblem, String createdByName,
		List<TeamView> teams, List<StandingView> standings) {}

	/** Someone who can be in a contest: on staff and linked to a Salon Ultimate name. */
	public record Eligible(Long userId, String name) {}

	private final ContestRepository contests;
	private final ContestEntryRepository entries;
	private final ContestTeamRepository teams;
	private final UserRepository users;
	private final SalonUltimateClient salonUltimate;
	private final PushService push;
	private final TeamBoardService teamBoard;
	private final ZoneId zone;

	public ContestService(ContestRepository contests, ContestEntryRepository entries, ContestTeamRepository teams,
			UserRepository users, SalonUltimateClient salonUltimate, PushService push, TeamBoardService teamBoard,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.contests = contests;
		this.entries = entries;
		this.teams = teams;
		this.users = users;
		this.salonUltimate = salonUltimate;
		this.push = push;
		this.teamBoard = teamBoard;
		this.zone = ZoneId.of(zone);
	}

	/** Live contests first, then upcoming, then the last few finished ones. */
	public List<ContestView> list(User viewer) {
		requireStaff(viewer);
		LocalDate today = today();
		List<Contest> all = contests.findAll();
		List<Contest> shown = new ArrayList<>();
		all.stream().filter(c -> isLive(c, today) || isEnded(c, today))
			.sorted(Comparator.comparing(Contest::getEndDate)).forEach(shown::add);
		all.stream().filter(c -> c.getStartDate().isAfter(today))
			.sorted(Comparator.comparing(Contest::getStartDate)).forEach(shown::add);
		all.stream().filter(c -> c.getFinishedAt() != null)
			.sorted(Comparator.comparing(Contest::getEndDate).reversed()).limit(10).forEach(shown::add);
		return shown.stream().map(c -> view(c, viewer)).toList();
	}

	public List<Eligible> eligible(User manager) {
		requireManager(manager);
		return users.findAll().stream()
			.filter(this::canEnter)
			.map(u -> new Eligible(u.getUserId(), name(u)))
			.sorted(Comparator.comparing((Eligible e) -> e.name().toLowerCase()))
			.toList();
	}

	@Transactional
	public ContestView create(User manager, ContestInput input) {
		requireManager(manager);
		Checked ok = validate(input);
		Contest c = new Contest();
		c.setCreatedBy(manager.getUserId());
		c.setCreatedAt(LocalDateTime.now(zone));
		apply(c, ok);
		Contest saved = contests.save(c);
		saveTeams(saved, ok.teamNames());
		for (PersonInput p : ok.people()) entries.save(entry(saved.getId(), p));
		return view(saved, manager);
	}

	/** Change a contest that isn't finished. Changing what's counted or when it starts clears the standings. */
	@Transactional
	public ContestView update(User manager, Long id, ContestInput input) {
		requireManager(manager);
		Contest c = find(id);
		if (c.getFinishedAt() != null) throw bad("A finished contest can't be changed");
		Checked ok = validate(input);
		boolean recount = c.getMetric() != ok.metric() || !c.getStartDate().equals(ok.startDate());
		apply(c, ok);
		if (recount || ok.teamNames().size() != teams.findByContestIdOrderByPositionAsc(id).size()) c.setLeadingTeam(null);
		contests.save(c);
		teams.deleteByContestId(id);
		saveTeams(c, ok.teamNames());

		Map<Long, ContestEntry> existing = entries.findByContestId(id).stream()
			.collect(Collectors.toMap(ContestEntry::getUserId, Function.identity()));
		Set<Long> keep = new HashSet<>();
		for (PersonInput p : ok.people()) {
			keep.add(p.userId());
			ContestEntry e = existing.get(p.userId());
			if (e == null) {
				entries.save(entry(id, p));
				continue;
			}
			e.setTeam(p.team());
			if (recount) {
				e.setCount(null);
				e.setRank(null);
			}
			entries.save(e);
		}
		existing.values().stream().filter(e -> !keep.contains(e.getUserId())).forEach(entries::delete);
		return view(c, manager);
	}

	@Transactional
	public void delete(User manager, Long id) {
		requireManager(manager);
		Contest c = find(id);
		entries.deleteByContestId(id);
		teams.deleteByContestId(id);
		contests.delete(c);
	}

	/** Re-read the standings now instead of waiting for the next scheduled check. */
	public ContestView checkNow(User manager, Long id) {
		requireManager(manager);
		Contest c = find(id);
		if (c.getFinishedAt() != null) throw bad("This contest is already finished");
		if (c.getStartDate().isAfter(today())) throw bad("This contest hasn't started yet");
		check(c);
		return view(find(id), manager);
	}

	/** Every four hours while the salon's open. */
	@Scheduled(cron = "0 0 8,12,16,20 * * *", zone = "${app.time-zone:America/Chicago}")
	public void checkAll() {
		for (Contest c : contests.findByFinishedAtIsNullAndStartDateLessThanEqual(today())) {
			try {
				check(c);
			} catch (Exception e) {
				log.warn("Checking contest {} failed", c.getId(), e);
			}
		}
	}

	/**
	 * Read the standings from Salon Ultimate, tell people whose place changed, and
	 * after the last day, announce the final results. One check at a time.
	 */
	synchronized void check(Contest c) {
		LocalDate today = today();
		boolean last = today.isAfter(c.getEndDate());
		LocalDate through = last ? c.getEndDate() : today;

		Map<String, SalonUltimateReport.ServiceTypes> counts = Map.of();
		String problem = null;
		if (!salonUltimate.isConfigured()) {
			problem = "Salon Ultimate sign-in isn't set up on the server (SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD).";
		} else {
			try {
				String html = salonUltimate.detailedServiceByEmployee(c.getStartDate(), through);
				counts = html == null ? Map.of() : SalonUltimateReport.parseDetailedService(html);
				String blocked = html == null || !counts.isEmpty() ? null : SalonUltimateReport.blockedBy(html);
				if (blocked != null) problem = "Salon Ultimate won't run the report until these timecards are closed: " + blocked;
			} catch (SalonUltimateClient.LoginFailed e) {
				problem = "Salon Ultimate didn't accept the sign-in. Check SALON_ULTIMATE_USERNAME and SALON_ULTIMATE_PASSWORD.";
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				problem = "The check was interrupted. It will try again at the next check.";
			} catch (Exception e) {
				log.warn("Salon Ultimate pull for contest {} failed", c.getId(), e);
				problem = "Couldn't reach Salon Ultimate: " + e.getMessage();
			}
		}
		if (problem != null) {
			c.setCheckProblem(problem.length() <= 500 ? problem : problem.substring(0, 500));
			contests.save(c);
			return;
		}

		List<ContestEntry> list = entries.findByContestId(c.getId());
		Map<Long, User> people = people(list);
		boolean firstCheck = list.stream().allMatch(e -> e.getRank() == null);
		Map<Long, Integer> before = new HashMap<>();
		for (ContestEntry e : list) {
			before.put(e.getId(), e.getRank());
			User u = people.get(e.getUserId());
			String su = u == null || u.getSalonUltimateName() == null ? null : u.getSalonUltimateName().toLowerCase();
			e.setCount(su == null ? 0 : value(counts.get(su), c.getMetric()));
		}
		for (ContestEntry e : list) {
			e.setRank(1 + (int) list.stream().filter(o -> o.getCount() > e.getCount()).count());
		}
		entries.saveAll(list);

		List<ContestTeam> teamList = teams.findByContestIdOrderByPositionAsc(c.getId());
		Map<Integer, Integer> totals = totals(teamList, list);
		Integer leader = leader(totals);
		Integer previousLeader = c.getLeadingTeam();
		c.setLeadingTeam(leader);
		c.setLastCheckedAt(LocalDateTime.now(zone));
		c.setCheckProblem(null);

		if (last) {
			c.setFinishedAt(LocalDateTime.now(zone));
			contests.save(c);
			announce(c, list, people, teamList, totals, leader);
			return;
		}
		contests.save(c);
		if (firstCheck) return;

		for (ContestEntry e : list) {
			Integer was = before.get(e.getId());
			if (was == null || was.equals(e.getRank())) continue;
			String place = place(e, list);
			String text = (e.getRank() < was ? "You moved up to " + place : "You're now " + place)
				+ " with " + c.getMetric().count(e.getCount()) + "." + toMoveUp(e, list, c.getMetric());
			push.notify(List.of(e.getUserId()), c.getName(), text, PAGE);
		}
		if (leader != null && !leader.equals(previousLeader)) {
			int second = totals.entrySet().stream().filter(t -> !t.getKey().equals(leader))
				.mapToInt(Map.Entry::getValue).max().orElse(0);
			push.notify(list.stream().map(ContestEntry::getUserId).toList(), c.getName(),
				teamName(teamList, leader) + " took the lead, " + totals.get(leader) + " to " + second + ".", PAGE);
		}
	}

	/** Final places to everyone in it, and the results on the team board. */
	private void announce(Contest c, List<ContestEntry> list, Map<Long, User> people, List<ContestTeam> teamList,
			Map<Integer, Integer> totals, Integer leader) {
		String teamResult = teamList.isEmpty() ? ""
			: leader == null ? " The teams tied." : " " + teamName(teamList, leader) + " won as a team.";
		for (ContestEntry e : list) {
			push.notify(List.of(e.getUserId()), c.getName() + " is over",
				"You finished " + place(e, list) + " with " + c.getMetric().count(e.getCount()) + "." + teamResult, PAGE);
		}

		List<String> lines = new ArrayList<>();
		lines.add(c.getName() + " results (" + c.getStartDate().format(SHORT_DATE) + " to " + c.getEndDate().format(SHORT_DATE) + ")");
		lines.add("");
		list.stream().sorted(standingOrder(people)).forEach(e ->
			lines.add(place(e, list) + ": " + nameOf(people, e.getUserId()) + ", " + c.getMetric().count(e.getCount())));
		if (!teamList.isEmpty()) {
			lines.add("");
			for (ContestTeam t : teamList) lines.add(t.getName() + ": " + c.getMetric().count(totals.get(t.getPosition())));
			lines.add(leader == null ? "The teams tied!" : teamName(teamList, leader) + " wins!");
		}
		if (c.getPrize() != null) {
			lines.add("");
			lines.add("Prize: " + c.getPrize());
		}
		User author = users.findById(c.getCreatedBy()).filter(User::isActive)
			.or(() -> users.findAll().stream().filter(u -> u.isActive() && u.getRoles() == Role.OWNER).findFirst())
			.orElse(null);
		if (author != null) teamBoard.announce(author, String.join("\n", lines));
	}

	private ContestView view(Contest c, User viewer) {
		List<ContestEntry> list = entries.findByContestId(c.getId());
		Map<Long, User> people = people(list);
		List<ContestTeam> teamList = teams.findByContestIdOrderByPositionAsc(c.getId());
		Map<Integer, Integer> totals = totals(teamList, list);
		boolean checked = c.getLastCheckedAt() != null;
		List<TeamView> teamViews = teamList.stream().map(t -> {
			int total = totals.get(t.getPosition());
			Integer rank = checked ? 1 + (int) totals.values().stream().filter(v -> v > total).count() : null;
			int members = (int) list.stream().filter(e -> t.getPosition().equals(e.getTeam())).count();
			return new TeamView(t.getPosition(), t.getName(), total, rank, members);
		}).toList();
		List<StandingView> standings = list.stream().sorted(standingOrder(people))
			.map(e -> new StandingView(e.getUserId(), nameOf(people, e.getUserId()), e.getCount(), e.getRank(), e.getTeam()))
			.toList();
		return new ContestView(c.getId(), c.getName(), c.getMetric().name(), c.getMetric().plural(), c.getStartDate(), c.getEndDate(),
			c.getPrize(), status(c), c.getLastCheckedAt(), viewer.isAdmin() ? c.getCheckProblem() : null,
			users.findById(c.getCreatedBy()).map(ContestService::name).orElse("Former employee"), teamViews, standings);
	}

	private record Checked(String name, Metric metric, LocalDate startDate, LocalDate endDate, String prize,
		List<String> teamNames, List<PersonInput> people) {}

	private Checked validate(ContestInput input) {
		if (input == null) throw bad("Fill in the contest");
		String name = trim(input.name());
		if (name == null) throw bad("Give the contest a name");
		if (name.length() > 80) throw bad("Keep the name to 80 characters");
		if (input.metric() == null) throw bad("Pick what's counted");
		LocalDate start = input.startDate();
		LocalDate end = input.endDate();
		if (start == null || end == null) throw bad("Pick the first and last day");
		if (end.isBefore(start)) throw bad("The last day can't be before the first day");
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw bad("Contests can run " + MAX_DAYS + " days at most");
		if (end.isBefore(today())) throw bad("The last day can't be in the past");
		String prize = trim(input.prize());
		if (prize != null && prize.length() > 200) throw bad("Keep the prize to 200 characters");

		List<String> teamNames = input.teams() == null ? List.of() : input.teams().stream().map(ContestService::trim).toList();
		if (teamNames.size() == 1) throw bad("Use at least two teams, or none");
		if (teamNames.size() > MAX_TEAMS) throw bad("Use " + MAX_TEAMS + " teams at most");
		if (teamNames.contains(null)) throw bad("Every team needs a name");
		if (teamNames.stream().anyMatch(t -> t.length() > 40)) throw bad("Keep team names to 40 characters");
		if (teamNames.stream().map(String::toLowerCase).distinct().count() < teamNames.size()) throw bad("Give each team its own name");

		List<PersonInput> people = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (PersonInput p : input.people() == null ? List.<PersonInput>of() : input.people()) {
			if (p == null || p.userId() == null || !seen.add(p.userId())) continue;
			User u = users.findById(p.userId()).orElse(null);
			if (u == null || !u.isActive() || !u.getRoles().isStaff()) throw bad("One of those people isn't on the team anymore");
			if (!canEnter(u)) throw bad(name(u) + " isn't linked to Salon Ultimate yet. Link them on the Team page first.");
			Integer team = teamNames.isEmpty() ? null : p.team();
			if (!teamNames.isEmpty() && (team == null || team < 0 || team >= teamNames.size())) throw bad("Put " + name(u) + " on a team");
			people.add(new PersonInput(u.getUserId(), team));
		}
		if (people.size() < 2) throw bad("Pick at least two people");
		for (int i = 0; i < teamNames.size(); i++) {
			int position = i;
			if (people.stream().noneMatch(p -> p.team() == position)) throw bad(teamNames.get(i) + " has no one on it");
		}
		return new Checked(name, input.metric(), start, end, prize, teamNames, people);
	}

	private static void apply(Contest c, Checked ok) {
		c.setName(ok.name());
		c.setMetric(ok.metric());
		c.setStartDate(ok.startDate());
		c.setEndDate(ok.endDate());
		c.setPrize(ok.prize());
	}

	private void saveTeams(Contest c, List<String> names) {
		for (int i = 0; i < names.size(); i++) {
			ContestTeam t = new ContestTeam();
			t.setContestId(c.getId());
			t.setPosition(i);
			t.setName(names.get(i));
			teams.save(t);
		}
	}

	private static ContestEntry entry(Long contestId, PersonInput p) {
		ContestEntry e = new ContestEntry();
		e.setContestId(contestId);
		e.setUserId(p.userId());
		e.setTeam(p.team());
		return e;
	}

	private String status(Contest c) {
		LocalDate today = today();
		if (c.getFinishedAt() != null) return "FINISHED";
		if (c.getStartDate().isAfter(today)) return "UPCOMING";
		return isEnded(c, today) ? "ENDED" : "LIVE";
	}

	private static boolean isLive(Contest c, LocalDate today) {
		return c.getFinishedAt() == null && !c.getStartDate().isAfter(today) && !c.getEndDate().isBefore(today);
	}

	private static boolean isEnded(Contest c, LocalDate today) {
		return c.getFinishedAt() == null && c.getEndDate().isBefore(today);
	}

	private static int value(SalonUltimateReport.ServiceTypes t, Metric metric) {
		if (t == null) return 0;
		return switch (metric) {
			case TREATMENTS -> t.treatments();
			case COLORS -> t.colors();
			case WAXES -> t.waxes();
		};
	}

	private static Map<Integer, Integer> totals(List<ContestTeam> teamList, List<ContestEntry> list) {
		Map<Integer, Integer> out = new HashMap<>();
		for (ContestTeam t : teamList) {
			out.put(t.getPosition(), list.stream()
				.filter(e -> t.getPosition().equals(e.getTeam()))
				.mapToInt(e -> e.getCount() == null ? 0 : e.getCount()).sum());
		}
		return out;
	}

	/** The team ahead of the rest, or null when there are no teams or the top is tied. */
	private static Integer leader(Map<Integer, Integer> totals) {
		int best = totals.values().stream().mapToInt(Integer::intValue).max().orElse(-1);
		List<Integer> top = totals.entrySet().stream().filter(t -> t.getValue() == best).map(Map.Entry::getKey).toList();
		return top.size() == 1 ? top.get(0) : null;
	}

	/** "2nd", or "tied for 2nd" when someone else has the same count. */
	private static String place(ContestEntry e, List<ContestEntry> list) {
		boolean tied = list.stream().anyMatch(o -> o != e && o.getCount().equals(e.getCount()));
		return (tied ? "tied for " : "") + ordinal(e.getRank());
	}

	/** " 2 more treatments to move up." for anyone not alone in first. */
	private static String toMoveUp(ContestEntry e, List<ContestEntry> list, Metric metric) {
		boolean tiedFirst = e.getRank() == 1 && list.stream().anyMatch(o -> o != e && o.getCount().equals(e.getCount()));
		if (tiedFirst) return " 1 more " + metric.word(1) + " takes the lead.";
		int above = list.stream().mapToInt(ContestEntry::getCount).filter(n -> n > e.getCount()).min().orElse(-1);
		if (above < 0) return "";
		int gap = above - e.getCount();
		return " " + gap + " more " + metric.word(gap) + " to move up.";
	}

	static String ordinal(int n) {
		int lastTwo = n % 100;
		String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th"
			: switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
		return n + suffix;
	}

	private static Comparator<ContestEntry> standingOrder(Map<Long, User> people) {
		return Comparator.comparing(ContestEntry::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(e -> nameOf(people, e.getUserId()).toLowerCase());
	}

	private static String teamName(List<ContestTeam> teamList, int position) {
		return teamList.stream().filter(t -> t.getPosition() == position).map(ContestTeam::getName).findFirst().orElse("A team");
	}

	private Map<Long, User> people(List<ContestEntry> list) {
		return users.findAllById(list.stream().map(ContestEntry::getUserId).toList()).stream()
			.collect(Collectors.toMap(User::getUserId, Function.identity()));
	}

	private boolean canEnter(User u) {
		return u.isActive() && u.getRoles().isStaff() && u.getSalonUltimateName() != null && !u.getSalonUltimateName().isBlank();
	}

	private Contest find(Long id) {
		return contests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That contest isn't here anymore"));
	}

	private LocalDate today() {
		return LocalDate.now(zone);
	}

	private static void requireStaff(User viewer) {
		if (!viewer.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contests are for staff");
	}

	private static void requireManager(User viewer) {
		if (!viewer.isAdmin()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners, general managers, and managers can run contests");
		}
	}

	private static String nameOf(Map<Long, User> people, Long userId) {
		User u = people.get(userId);
		return u == null ? "Former employee" : name(u);
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
