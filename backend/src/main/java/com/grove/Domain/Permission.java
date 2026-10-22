package com.grove.Domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Things the owner decides, per role, on the Settings page. Owners always have
 * every permission so they can't lock themselves out.
 */
public enum Permission {
	VIEW_SCHEDULE("See the Schedule tab",
		"View the posted salon schedule.",
		EnumSet.of(Role.GENERAL_MANAGER, Role.MANAGER, Role.LEAD, Role.STYLIST, Role.RECEPTIONIST)),
	EDIT_SCHEDULE("Build the schedule",
		"Fill in weeks, save drafts, and send them for approval.",
		EnumSet.of(Role.GENERAL_MANAGER)),
	APPROVE_SCHEDULE("Approve and post the schedule",
		"Approve weeks sent to them, post weeks to staff, and change posted weeks.",
		EnumSet.of(Role.GENERAL_MANAGER));

	private final String label;
	private final String description;
	private final Set<Role> defaults;

	Permission(String label, String description, Set<Role> defaults) {
		this.label = label;
		this.description = description;
		this.defaults = defaults;
	}

	public String label() { return label; }
	public String description() { return description; }
	public Set<Role> defaults() { return EnumSet.copyOf(defaults); }

	/** Settings-table key, e.g. "permission.EDIT_SCHEDULE". */
	public String key() { return "permission." + name(); }
}
