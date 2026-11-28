package com.grove.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.NotificationKind;
import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;

/**
 * Which kinds of notifications each person wants. Everything starts on; turning a kind
 * off stops it for that person only, both on their phone and on their Notifications list.
 */
@Service
public class NotificationSettingsService {

	/** One kind on My account: what it covers and whether you get it. */
	public record Choice(NotificationKind kind, String label, String description, boolean on) {}

	public record ChoiceInput(NotificationKind kind, boolean on) {}

	private final AppSettingRepository settings;
	private final PermissionService perms;

	public NotificationSettingsService(AppSettingRepository settings, PermissionService perms) {
		this.settings = settings;
		this.perms = perms;
	}

	/** People who turned this kind off. */
	public Set<Long> off(NotificationKind kind) {
		Set<Long> out = new HashSet<>();
		if (kind == null) return out;
		settings.findById(kind.settingKey()).ifPresent(s -> {
			if (s.getValue() == null) return;
			for (String part : s.getValue().split(",")) {
				try {
					if (!part.isBlank()) out.add(Long.parseLong(part.trim()));
				} catch (NumberFormatException e) {
					// Not an id: ignore it.
				}
			}
		});
		return out;
	}

	public boolean isOn(Long userId, NotificationKind kind) {
		return !off(kind).contains(userId);
	}

	/** Turn one kind on or off for one person. Synchronized so two saves at once don't undo each other. */
	public synchronized void set(Long userId, NotificationKind kind, boolean on) {
		if (userId == null || kind == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a kind of notification");
		}
		Set<Long> list = new TreeSet<>(off(kind));
		boolean changed = on ? list.remove(userId) : list.add(userId);
		if (!changed) return;
		settings.save(new AppSetting(kind.settingKey(), String.join(",", list.stream().map(String::valueOf).toList())));
	}

	/** The kinds that can reach this person, for My account. */
	public List<Choice> choices(User user) {
		return Arrays.stream(NotificationKind.values())
			.filter(k -> canGet(user, k))
			.map(k -> new Choice(k, k.label(), k.description(), isOn(user.getUserId(), k)))
			.toList();
	}

	public List<Choice> update(User user, ChoiceInput input) {
		if (input == null || input.kind() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a kind of notification");
		}
		set(user.getUserId(), input.kind(), input.on());
		return choices(user);
	}

	/**
	 * Whether anything of this kind is ever sent to this person, so My account only lists
	 * what applies to them. Matches who each part of the site notifies.
	 */
	boolean canGet(User u, NotificationKind kind) {
		if (u == null || !u.isActive()) return false;
		Role role = u.getRoles();
		return switch (kind) {
			case TIME_OFF_REQUESTS -> perms.has(u, Permission.APPROVE_TIME_OFF);
			case PTO_REQUESTS, DAILY_SALES, JOB_APPLICATIONS -> role == Role.OWNER;
			case MY_REQUESTS -> role.isScheduled();
			case SCHEDULE -> perms.has(u, Permission.VIEW_SCHEDULE) || perms.has(u, Permission.EDIT_SCHEDULE)
				|| perms.has(u, Permission.APPROVE_SCHEDULE);
			case PAYROLL_NOTES -> perms.has(u, Permission.PAYROLL_NOTES) || perms.has(u, Permission.VIEW_PAYROLL);
			case VIDEOS, TEAM_BOARD -> role.isStaff();
			case CONTESTS -> role.isStaff() && role != Role.RECEPTIONIST;
			case OPEN_TIMECARDS -> perms.has(u, Permission.TIMECARD_ALERTS);
		};
	}
}
