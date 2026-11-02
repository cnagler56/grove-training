package com.grove.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.TimeOffDecision;
import com.grove.Domain.Dtos.VacationPayInput;
import com.grove.Domain.Dtos.VacationPayQueue;
import com.grove.Domain.Dtos.VacationPayView;
import com.grove.Service.SessionService;
import com.grove.Service.VacationPayService;

/** The Vacation pay tab: employees ask for hours on a check date; owners answer. */
@RestController
@RequestMapping("/api/vacation-pay")
public class VacationPayController {

	private final VacationPayService vacationPay;
	private final SessionService sessions;

	public VacationPayController(VacationPayService vacationPay, SessionService sessions) {
		this.vacationPay = vacationPay;
		this.sessions = sessions;
	}

	@GetMapping("/check-dates")
	public List<LocalDate> checkDates(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireUser(token);
		return vacationPay.checkDates();
	}

	@GetMapping("/mine")
	public List<VacationPayView> mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return vacationPay.mine(sessions.requireUser(token));
	}

	@PostMapping
	public ResponseEntity<VacationPayView> request(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody VacationPayInput input) {
		return ResponseEntity.status(HttpStatus.CREATED).body(vacationPay.request(sessions.requireUser(token), input));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> cancel(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		vacationPay.cancel(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/queue")
	public VacationPayQueue queue(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return vacationPay.queue();
	}

	@PutMapping("/{id}/approve")
	public VacationPayView approve(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return vacationPay.approve(sessions.requireOwner(token), id);
	}

	@PutMapping("/{id}/send-back")
	public VacationPayView sendBack(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody TimeOffDecision body) {
		return vacationPay.sendBack(sessions.requireOwner(token), id, body == null ? null : body.message());
	}
}
