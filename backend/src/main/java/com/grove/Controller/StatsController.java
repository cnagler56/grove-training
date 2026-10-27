package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.MyStats;
import com.grove.Domain.Dtos.SalonUltimateLink;
import com.grove.Domain.Dtos.SalonUltimateName;
import com.grove.Domain.Dtos.StatsImportView;
import com.grove.Domain.Dtos.TeamMember;
import com.grove.Domain.Dtos.TeamStats;
import com.grove.Domain.User;
import com.grove.Service.SessionService;
import com.grove.Service.StatsService;
import com.grove.Service.UserService;

/**
 * Weekly numbers from Salon Ultimate. Staff see their own ("My week");
 * managers and up see everyone's leaderboards, can pull a week by hand, and
 * link each person to their Salon Ultimate name.
 */
@RestController
public class StatsController {

	private final StatsService stats;
	private final SessionService sessions;
	private final UserService users;

	public StatsController(StatsService stats, SessionService sessions, UserService users) {
		this.stats = stats;
		this.sessions = sessions;
		this.users = users;
	}

	@GetMapping("/api/stats/mine")
	public MyStats mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User user = sessions.requireUser(token);
		if (!user.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
		return stats.mine(user);
	}

	@GetMapping("/api/stats/team")
	public TeamStats team(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
		sessions.requireAdmin(token);
		return stats.team(week);
	}

	/** Pull (or re-pull) one finished week from Salon Ultimate now. */
	@PostMapping("/api/stats/import")
	public StatsImportView pull(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
		sessions.requireAdmin(token);
		return stats.importWeek(week);
	}

	@GetMapping("/api/stats/names")
	public List<SalonUltimateName> names(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return stats.names();
	}

	/** Link someone to their Salon Ultimate name (blank to unlink). */
	@PutMapping("/api/admin/team/{userId}/salon-ultimate")
	public TeamMember link(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long userId, @RequestBody SalonUltimateLink body) {
		User actor = sessions.requireAdmin(token);
		stats.link(users.manageable(actor, userId), body.name());
		return users.member(userId);
	}
}
