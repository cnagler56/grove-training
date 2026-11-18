package com.grove.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
}
