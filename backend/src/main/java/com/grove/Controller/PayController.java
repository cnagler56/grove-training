package com.grove.Controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.BumpInput;
import com.grove.Domain.Dtos.MinimumInput;
import com.grove.Domain.Dtos.PayPersonInput;
import com.grove.Domain.Dtos.PayRules;
import com.grove.Domain.Dtos.PayRulesUpload;
import com.grove.Service.PayService;
import com.grove.Service.SessionService;

/**
 * The pay matrix and pay codes, loaded by the owner from the payroll skill's
 * files. Owners only; each stylist sees just their own weekly rate on My week.
 */
@RestController
public class PayController {

	private final PayService pay;
	private final SessionService sessions;

	public PayController(PayService pay, SessionService sessions) {
		this.pay = pay;
		this.sessions = sessions;
	}

	@GetMapping("/api/owner/pay-rules")
	public PayRules rules(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return pay.rules();
	}

	/** The lead or manager bump. */
	@PutMapping("/api/owner/pay-rules/bump")
	public PayRules bump(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody BumpInput body) {
		sessions.requireOwner(token);
		return pay.setBump(body == null ? null : body.role(), body == null ? null : body.amount());
	}

	/** The lowest rate a stylist is paid. */
	@PutMapping("/api/owner/pay-rules/minimum")
	public PayRules minimum(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody MinimumInput body) {
		sessions.requireOwner(token);
		return pay.setMinimum(body == null ? null : body.amount());
	}

	/** The default rate for receptionists. */
	@PutMapping("/api/owner/pay-rules/receptionist-rate")
	public PayRules receptionistRate(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody MinimumInput body) {
		sessions.requireOwner(token);
		return pay.setReceptionistRate(body == null ? null : body.amount());
	}

	/** Set someone's own base rate, or with a null rate, put them back on their role's default. */
	@PutMapping("/api/owner/pay-rules/people")
	public PayRules baseRate(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayPersonInput body) {
		sessions.requireOwner(token);
		return pay.setBaseRate(body);
	}

	@PutMapping("/api/owner/pay-rules")
	public PayRules load(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayRulesUpload upload) {
		sessions.requireOwner(token);
		return pay.load(upload);
	}
}
