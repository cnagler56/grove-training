package com.grove.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.AppSetting;
import com.grove.Domain.ClosedDay;
import com.grove.Domain.Holiday;
import com.grove.Domain.Permission;
import com.grove.Domain.User;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.ClosedDayRepository;

/**
 * Days the salon is closed. Holidays the owner checks on Settings are closed every year;
 * the time off calendar marks other days closed (a snow day) or opens a holiday for one
 * year. The month's projected sales and the daily goals leave closed days out.
 */
@Service
public class ClosedDayService {

	/** Holidays the salon always closes for, kept as "THANKSGIVING,CHRISTMAS_DAY". */
	static final String HOLIDAYS_KEY = "closed.holidays";
	/** The most days one request can ask about. */
	private static final int MAX_DAYS = 731;
	private static final int MAX_NOTE = 200;

	/**
	 * One day on the calendar. {@code reason}: what to show ("Christmas Day", "Snow day"),
	 * or null. {@code holiday}: the checked holiday on this date, even if it's open this year.
	 */
	public record ClosedDayView(LocalDate date, boolean closed, String reason, String holiday) {}

	public record ClosedDayInput(boolean closed, String note) {}

	/** One holiday on Settings: its next date and whether the salon always closes for it. */
	public record HolidayChoice(Holiday holiday, String label, LocalDate next, boolean on) {}

	public record HolidayInput(Holiday holiday, boolean on) {}

	private final ClosedDayRepository days;
	private final AppSettingRepository settings;
	private final PermissionService perms;
	private final ZoneId zone;

	public ClosedDayService(ClosedDayRepository days, AppSettingRepository settings, PermissionService perms,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.days = days;
		this.settings = settings;
		this.perms = perms;
		this.zone = ZoneId.of(zone);
	}

	/** Closed days from {@code from} to {@code to}, each with what to show for it. */
	public Map<LocalDate, String> closedBetween(LocalDate from, LocalDate to) {
		Map<LocalDate, String> out = new LinkedHashMap<>();
		for (ClosedDayView v : between(from, to)) {
			if (v.closed()) out.put(v.date(), v.reason() == null ? "Closed" : v.reason());
		}
		return out;
	}

	/** Closed days, and holidays opened for this year, from {@code from} to {@code to}. */
	public List<ClosedDayView> between(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the dates to show");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask for two years or less at a time");
		}
		Map<LocalDate, String> holidayDates = holidayDates(from, to);
		Map<LocalDate, ClosedDay> marked = days.findByDateBetweenOrderByDateAsc(from, to).stream()
			.collect(Collectors.toMap(ClosedDay::getDate, Function.identity()));
		Set<LocalDate> dates = new TreeSet<>(holidayDates.keySet());
		dates.addAll(marked.keySet());
		List<ClosedDayView> out = new ArrayList<>();
		for (LocalDate d : dates) {
			String holiday = holidayDates.get(d);
			ClosedDay m = marked.get(d);
			if (m == null) {
				out.add(new ClosedDayView(d, true, holiday, holiday));
			} else if (m.isClosed()) {
				out.add(new ClosedDayView(d, true, m.getNote() != null ? m.getNote() : holiday, holiday));
			} else if (holiday != null) {
				// Opened for this year. A leftover "open" mark for a holiday no longer checked means nothing.
				out.add(new ClosedDayView(d, false, null, holiday));
			}
		}
		return out;
	}

	/**
	 * Mark a day closed (with an optional reason) or open. Opening a checked holiday keeps
	 * it open that year only. For the people who approve time off, who use the calendar.
	 */
	public ClosedDayView set(User actor, LocalDate date, ClosedDayInput input) {
		perms.require(actor, Permission.APPROVE_TIME_OFF, "Only people who approve time off can mark the salon closed");
		if (date == null || input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a day");
		String note = input.note() == null || input.note().isBlank() ? null : input.note().trim();
		if (note != null && note.length() > MAX_NOTE) note = note.substring(0, MAX_NOTE);
		boolean holiday = holidayDates(date, date).containsKey(date);
		boolean needsMark = input.closed() ? !holiday || note != null : holiday;
		if (needsMark) {
			ClosedDay d = days.findById(date).orElseGet(() -> new ClosedDay(date, null, null, actor.getUserId(), LocalDateTime.now(zone)));
			d.setClosed(input.closed());
			d.setNote(input.closed() ? note : null);
			days.save(d);
		} else {
			days.findById(date).ifPresent(days::delete);
		}
		return between(date, date).stream().findFirst().orElse(new ClosedDayView(date, false, null, null));
	}

	/** Every holiday, its next date, and whether it's checked, for Settings. */
	public List<HolidayChoice> holidayChoices() {
		LocalDate today = LocalDate.now(zone);
		Set<Holiday> on = holidays();
		return Arrays.stream(Holiday.values()).map(h -> {
			LocalDate next = h.on(today.getYear());
			if (next.isBefore(today)) next = h.on(today.getYear() + 1);
			return new HolidayChoice(h, h.label(), next, on.contains(h));
		}).toList();
	}

	/** Check or uncheck one holiday (owner, on Settings). */
	public synchronized List<HolidayChoice> setHoliday(HolidayInput input) {
		if (input == null || input.holiday() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a holiday");
		Set<Holiday> on = EnumSet.noneOf(Holiday.class);
		on.addAll(holidays());
		if (input.on()) on.add(input.holiday());
		else on.remove(input.holiday());
		settings.save(new AppSetting(HOLIDAYS_KEY, on.stream().map(Holiday::name).collect(Collectors.joining(","))));
		return holidayChoices();
	}

	/** The holidays the salon always closes for. */
	Set<Holiday> holidays() {
		Set<Holiday> out = EnumSet.noneOf(Holiday.class);
		settings.findById(HOLIDAYS_KEY).ifPresent(s -> {
			if (s.getValue() == null) return;
			for (String part : s.getValue().split(",")) {
				try {
					if (!part.isBlank()) out.add(Holiday.valueOf(part.trim()));
				} catch (IllegalArgumentException e) {
					// No longer a holiday on the list: ignore it.
				}
			}
		});
		return out;
	}

	/** Checked holidays falling from {@code from} to {@code to}, with their names. */
	private Map<LocalDate, String> holidayDates(LocalDate from, LocalDate to) {
		Map<LocalDate, String> out = new TreeMap<>();
		Set<Holiday> on = holidays();
		for (int year = from.getYear(); year <= to.getYear(); year++) {
			for (Holiday h : on) {
				LocalDate d = h.on(year);
				if (!d.isBefore(from) && !d.isAfter(to)) out.put(d, h.label());
			}
		}
		return out;
	}
}
