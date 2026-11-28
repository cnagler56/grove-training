package com.grove.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.Dtos.Person;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.UserRepository;

/**
 * Who can be picked as manager on duty (MOD) on the schedule. The owner checks
 * people on the Settings page; the list is stored in app_settings as employee ids.
 * Until she saves it, every general manager and manager can be MOD. Only people
 * who are on the schedule (not owners or customers) can be on the list.
 */
@Service
public class ManagerOnDutyService {

	static final String KEY = "schedule.modEligible";
	/** Who can be MOD until the owner saves her own list. */
	private static final Set<Role> STARTING_ROLES = EnumSet.of(Role.GENERAL_MANAGER, Role.MANAGER);

	/** One person on the Settings card, checked or not. */
	public record Candidate(Long userId, String name, Role role, boolean on) {}

	public record CandidateInput(Long userId, boolean on) {}

	private final AppSettingRepository settings;
	private final UserRepository users;

	public ManagerOnDutyService(AppSettingRepository settings, UserRepository users) {
		this.settings = settings;
		this.users = users;
	}

	/** Can be picked as MOD now: on the owner's list and on the schedule (not marked off it for time away). */
	public boolean isEligible(User u) {
		return u != null && u.isActive() && u.getRoles().isScheduled() && u.getInactiveSince() == null
			&& onList(u, saved());
	}

	/** Everyone who can be picked as MOD, A to Z: the schedule editor's dropdown. */
	public List<Person> choices() {
		return staff().stream().filter(this::isEligible)
			.map(u -> new Person(u.getUserId(), name(u), u.getRoles()))
			.toList();
	}

	/** Everyone on the schedule, checked if they're on the MOD list: the owner's Settings card. */
	public List<Candidate> candidates() {
		Optional<Set<Long>> saved = saved();
		return staff().stream()
			.map(u -> new Candidate(u.getUserId(), name(u), u.getRoles(), onList(u, saved)))
			.toList();
	}

	private static boolean onList(User u, Optional<Set<Long>> saved) {
		return saved.map(ids -> ids.contains(u.getUserId())).orElse(STARTING_ROLES.contains(u.getRoles()));
	}

	/** Owner checks or unchecks one person. Synchronized so two quick clicks don't undo each other. */
	public synchronized List<Candidate> set(CandidateInput input) {
		if (input == null || input.userId() == null) throw bad("Pick someone");
		User u = users.findById(input.userId()).orElseThrow(() -> bad("That person isn't on the team"));
		if (input.on() && (!u.isActive() || !u.getRoles().isScheduled())) {
			throw bad(name(u) + " isn't on the schedule, so they can't be manager on duty");
		}
		// The first change starts from the starting roles, so nobody drops off unexpectedly.
		Optional<Set<Long>> saved = saved();
		Set<Long> ids = new TreeSet<>(saved.orElseGet(() -> staff().stream().filter(p -> onList(p, saved))
			.map(User::getUserId).collect(Collectors.toSet())));
		if (input.on()) ids.add(u.getUserId()); else ids.remove(u.getUserId());
		settings.save(new AppSetting(KEY, ids.stream().map(String::valueOf).collect(Collectors.joining(","))));
		return candidates();
	}

	/** The owner's saved list, or empty if she hasn't saved one yet. */
	private Optional<Set<Long>> saved() {
		return settings.findById(KEY).map(s -> {
			Set<Long> ids = new TreeSet<>();
			if (s.getValue() == null) return ids;
			for (String part : s.getValue().split(",")) {
				try {
					if (!part.isBlank()) ids.add(Long.parseLong(part.trim()));
				} catch (NumberFormatException e) {
					// Not an id: ignore it.
				}
			}
			return ids;
		});
	}

	/** Active people on the schedule, A to Z. */
	private List<User> staff() {
		return users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles().isScheduled())
			.sorted(Comparator.comparing(u -> name(u).toLowerCase()))
			.toList();
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
			+ (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static ResponseStatusException bad(String msg) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}
}
