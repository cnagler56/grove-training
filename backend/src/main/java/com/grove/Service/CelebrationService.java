package com.grove.Service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.grove.Domain.Dtos.Celebration;
import com.grove.Domain.Dtos.Celebrations;
import com.grove.Domain.User;
import com.grove.Repository.UserRepository;

/**
 * Birthdays and work anniversaries from the details on each employee's Team
 * page. Everyone on staff sees today's; managers and up also see the next 7
 * days. Only active staff are included, ages are never shown, and work
 * anniversaries start at one year.
 */
@Service
public class CelebrationService {

	public static final String BIRTHDAY = "BIRTHDAY";
	public static final String ANNIVERSARY = "ANNIVERSARY";
	private static final int UPCOMING_DAYS = 7;

	private final UserRepository users;
	private final ZoneId zone;

	public CelebrationService(UserRepository users, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.users = users;
		this.zone = ZoneId.of(zone);
	}

	/** Today in the salon's time zone. */
	public LocalDate today() {
		return LocalDate.now(zone);
	}

	public Celebrations forViewer(User viewer) {
		LocalDate today = today();
		if (!viewer.getRoles().isStaff()) return new Celebrations(today, List.of(), List.of());
		List<Celebration> all = between(today, today.plusDays(UPCOMING_DAYS));
		List<Celebration> todays = all.stream().filter(c -> c.date().equals(today)).toList();
		List<Celebration> upcoming = viewer.isAdmin()
			? all.stream().filter(c -> c.date().isAfter(today)).toList()
			: List.of();
		return new Celebrations(today, todays, upcoming);
	}

	/** Every birthday and anniversary from {@code from} through {@code to}, by date. */
	List<Celebration> between(LocalDate from, LocalDate to) {
		List<Celebration> out = new ArrayList<>();
		for (User u : users.findAll()) {
			if (!u.isActive() || !u.getRoles().isStaff()) continue;
			for (int year = from.getYear(); year <= to.getYear(); year++) {
				if (u.getBirthday() != null) {
					LocalDate d = occurrence(u.getBirthday(), year);
					if (!d.isBefore(from) && !d.isAfter(to)) out.add(new Celebration(u.getUserId(), name(u), BIRTHDAY, d, null));
				}
				if (u.getHireDate() != null) {
					LocalDate d = occurrence(u.getHireDate(), year);
					int years = year - u.getHireDate().getYear();
					if (years >= 1 && !d.isBefore(from) && !d.isAfter(to)) {
						out.add(new Celebration(u.getUserId(), name(u), ANNIVERSARY, d, years));
					}
				}
			}
		}
		out.sort(Comparator.comparing(Celebration::date).thenComparing(Celebration::name));
		return out;
	}

	/** The date's anniversary in {@code year}. February 29 falls on February 28 in other years. */
	static LocalDate occurrence(LocalDate original, int year) {
		MonthDay md = MonthDay.from(original);
		return md.isValidYear(year) ? md.atYear(year) : LocalDate.of(year, 2, 28);
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
			+ (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}
}
