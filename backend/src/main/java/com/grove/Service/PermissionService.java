package com.grove.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.Dtos.MyPermissions;
import com.grove.Domain.Dtos.PermissionRule;
import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;

/**
 * Which roles have each {@link Permission}, as set by the owner. Stored in
 * app_settings as comma-separated role names; until the owner saves anything,
 * each permission uses its defaults. Owners always have every permission.
 */
@Service
public class PermissionService {

	private final AppSettingRepository settings;

	public PermissionService(AppSettingRepository settings) {
		this.settings = settings;
	}

	/** Roles with this permission (always including OWNER). */
	public Set<Role> roles(Permission p) {
		Set<Role> roles = settings.findById(p.key())
			.map(s -> parse(s.getValue()))
			.orElseGet(p::defaults);
		roles.add(Role.OWNER);
		return roles;
	}

	public boolean has(User user, Permission p) {
		return user != null && user.isActive() && roles(p).contains(user.getRoles());
	}

	public void require(User user, Permission p, String message) {
		if (!has(user, p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}

	public MyPermissions forUser(User user) {
		return new MyPermissions(has(user, Permission.VIEW_SCHEDULE), has(user, Permission.EDIT_SCHEDULE),
			has(user, Permission.APPROVE_SCHEDULE));
	}

	/** Every permission with its current roles, for the owner's Settings page. */
	public List<PermissionRule> rules() {
		return Arrays.stream(Permission.values())
			.map(p -> new PermissionRule(p, p.label(), p.description(),
				Arrays.stream(Role.values()).filter(roles(p)::contains).toList()))
			.toList();
	}

	/** Owner saves the roles for some permissions. OWNER is always kept; CUSTOMER can't be given schedule rights. */
	public List<PermissionRule> update(Map<Permission, List<Role>> changes) {
		changes.forEach((p, roles) -> {
			Set<Role> set = EnumSet.noneOf(Role.class);
			if (roles != null) set.addAll(roles);
			if (set.contains(Role.CUSTOMER)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customers can't be given schedule access");
			}
			set.add(Role.OWNER);
			settings.save(new AppSetting(p.key(), set.stream().map(Role::name).sorted().collect(Collectors.joining(","))));
		});
		return rules();
	}

	private static Set<Role> parse(String value) {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		if (value == null || value.isBlank()) return roles;
		for (String name : value.split(",")) {
			try {
				roles.add(Role.valueOf(name.trim()));
			} catch (IllegalArgumentException e) {
				// A role that no longer exists: ignore it.
			}
		}
		return roles;
	}
}
