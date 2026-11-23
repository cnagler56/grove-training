package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.DailySalesView;
import com.grove.Service.DailySalesService;
import com.grove.Service.SessionService;

/** The owner's daily business card on Home. */
@RestController
public class DailySalesController {

	private final DailySalesService dailySales;
	private final SessionService sessions;

	public DailySalesController(DailySalesService dailySales, SessionService sessions) {
		this.dailySales = dailySales;
		this.sessions = sessions;
	}

	/** Which owners get the daily sales summary notification (Settings). */
	@GetMapping("/api/owner/daily-sales/recipients")
	public List<DailySalesService.SummaryRecipient> recipients(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return dailySales.recipients();
	}

	@PutMapping("/api/owner/daily-sales/recipients")
	public List<DailySalesService.SummaryRecipient> setRecipient(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody DailySalesService.SummaryRecipientInput body) {
		sessions.requireOwner(token);
		return dailySales.setRecipient(body == null ? null : body.userId(), body != null && body.on());
	}

	@GetMapping("/api/owner/daily-sales")
	public DailySalesView dailySales(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return dailySales.view();
	}

	/** Try again on a day that didn't come in, e.g. after a late timecard is closed. */
	@PostMapping("/api/owner/daily-sales/{date}/retry")
	public DailySalesView retry(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable LocalDate date) {
		sessions.requireOwner(token);
		return dailySales.retry(date);
	}
}
