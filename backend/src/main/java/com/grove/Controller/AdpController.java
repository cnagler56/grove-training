package com.grove.Controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.AdpCompanyInput;
import com.grove.Domain.Dtos.AdpFile;
import com.grove.Domain.Dtos.AdpPersonInput;
import com.grove.Domain.Dtos.AdpSettings;
import com.grove.Domain.Permission;
import com.grove.Service.AdpService;
import com.grove.Service.PermissionService;
import com.grove.Service.SessionService;

/**
 * The ADP import file: the owner's ADP settings (company code, each person's employee ID,
 * fixed pay, monthly bonus), and the file itself on the Payroll tab.
 */
@RestController
public class AdpController {

	private final AdpService adp;
	private final SessionService sessions;
	private final PermissionService perms;

	public AdpController(AdpService adp, SessionService sessions, PermissionService perms) {
		this.adp = adp;
		this.sessions = sessions;
		this.perms = perms;
	}

	@GetMapping("/api/owner/adp")
	public AdpSettings settings(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return adp.settings();
	}

	@PutMapping("/api/owner/adp/company")
	public AdpSettings company(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody AdpCompanyInput body) {
		sessions.requireOwner(token);
		return adp.setCompanyCode(body == null ? null : body.companyCode());
	}

	@PutMapping("/api/owner/adp/people")
	public AdpSettings person(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody AdpPersonInput body) {
		sessions.requireOwner(token);
		return adp.setPerson(body);
	}

	/** What the file for this check holds, and anything to fix before it can be downloaded. */
	@GetMapping("/api/payroll/adp")
	public AdpFile file(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate checkDate) {
		payrollUser(token);
		return adp.file(checkDate);
	}

	/** The .csv to import into ADP. 409 while anything on the Payroll tab still needs fixing. */
	@GetMapping("/api/payroll/adp/file")
	public ResponseEntity<byte[]> download(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate checkDate) {
		payrollUser(token);
		AdpFile f = adp.file(checkDate);
		byte[] body = AdpService.csvOf(f).getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(body);
	}

	private void payrollUser(String token) {
		perms.require(sessions.requireUser(token), Permission.VIEW_PAYROLL, "No access to the Payroll tab");
	}
}
