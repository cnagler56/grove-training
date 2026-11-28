package com.grove.Domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/**
 * Holidays the owner can mark as "always closed" on Settings. Each one that's checked
 * is a closed day every year, unless that year's date is opened on the time off calendar.
 */
public enum Holiday {
	NEW_YEARS_DAY("New Year's Day"),
	EASTER("Easter"),
	MEMORIAL_DAY("Memorial Day"),
	INDEPENDENCE_DAY("Fourth of July"),
	LABOR_DAY("Labor Day"),
	THANKSGIVING("Thanksgiving"),
	CHRISTMAS_EVE("Christmas Eve"),
	CHRISTMAS_DAY("Christmas Day"),
	NEW_YEARS_EVE("New Year's Eve");

	private final String label;

	Holiday(String label) {
		this.label = label;
	}

	public String label() { return label; }

	/** This holiday's date in a year. */
	public LocalDate on(int year) {
		return switch (this) {
			case NEW_YEARS_DAY -> LocalDate.of(year, 1, 1);
			case EASTER -> easter(year);
			case MEMORIAL_DAY -> LocalDate.of(year, Month.MAY, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
			case INDEPENDENCE_DAY -> LocalDate.of(year, 7, 4);
			case LABOR_DAY -> LocalDate.of(year, Month.SEPTEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
			case THANKSGIVING -> LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
			case CHRISTMAS_EVE -> LocalDate.of(year, 12, 24);
			case CHRISTMAS_DAY -> LocalDate.of(year, 12, 25);
			case NEW_YEARS_EVE -> LocalDate.of(year, 12, 31);
		};
	}

	/** Easter Sunday (the Gregorian calendar's rule, as churches in the US use). */
	static LocalDate easter(int year) {
		int a = year % 19;
		int b = year / 100;
		int c = year % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int n = h + l - 7 * m + 114;
		return LocalDate.of(year, n / 31, n % 31 + 1);
	}
}
