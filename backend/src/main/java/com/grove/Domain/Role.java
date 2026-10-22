package com.grove.Domain;

/**
 * Salon roles, highest first. {@code rank} decides who can manage whom: you
 * can only change people ranked below you and only hand out roles below your
 * own (an OWNER can do anything). Stylists and receptionists share a rank.
 */
public enum Role {
	OWNER(6, "Owner"),
	GENERAL_MANAGER(5, "General manager"),
	MANAGER(4, "Manager"),
	LEAD(3, "Lead"),
	STYLIST(2, "Stylist"),
	RECEPTIONIST(2, "Receptionist"),
	CUSTOMER(1, "Customer");

	private final int rank;
	private final String label;

	Role(int rank, String label) {
		this.rank = rank;
		this.label = label;
	}

	public String label() { return label; }

	public boolean outranks(Role other) { return rank > other.rank; }

	/** Managers and up get the manager pages: upload videos, manage the team. */
	public boolean canManageSite() { return rank >= MANAGER.rank; }

	/** Who appears on the weekly schedule: everyone but owners and customers. */
	public boolean isScheduled() {
		return this == GENERAL_MANAGER || this == MANAGER || this == LEAD || this == STYLIST || this == RECEPTIONIST;
	}

	/** Training videos are for staff only. */
	public boolean isStaff() { return this != CUSTOMER; }
}
