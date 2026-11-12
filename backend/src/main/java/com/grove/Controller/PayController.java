package com.grove.Controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.BumpInput;
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

	/** Add or change one person's pay. */
	@PutMapping("/api/owner/pay-rules/people")
	public PayRules savePerson(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayPersonInput body) {
		sessions.requireOwner(token);
		return pay.savePerson(body);
	}

	@DeleteMapping("/api/owner/pay-rules/people")
	public PayRules removePerson(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam String name) {
		sessions.requireOwner(token);
		return pay.removePerson(name);
	}

	@PutMapping("/api/owner/pay-rules")
	public PayRules load(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayRulesUpload upload) {
		sessions.requireOwner(token);
		return pay.load(upload);
	}
}
