package com.grove.Domain;

/** What a schedule entry is. */
public enum ShiftType {
	/** Working a shift, with start and end times. */
	WORK,
	/** "RO": the person requested the day off. No times; shown so the team knows. */
	REQUESTED_OFF
}
