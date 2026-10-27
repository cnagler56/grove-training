package com.grove.Controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

	@PutMapping("/api/owner/pay-rules")
	public PayRules load(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayRulesUpload upload) {
		sessions.requireOwner(token);
		return pay.load(upload);
	}
}
