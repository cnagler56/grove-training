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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.PayPeriods;
import com.grove.Domain.Dtos.PayrollNoteInput;
import com.grove.Domain.Dtos.PayrollNoteView;
import com.grove.Domain.Dtos.PayrollReport;
import com.grove.Domain.Dtos.PayrollSummary;
import com.grove.Domain.Dtos.StatsImportView;
import com.grove.Domain.Dtos.Person;
import com.grove.Domain.Permission;
import com.grove.Domain.User;
import com.grove.Service.PayrollService;
import com.grove.Service.PermissionService;
import com.grove.Service.SessionService;

/**
 * The Payroll notes tab (deductions, additions, comments) and the Payroll tab.
 * Who gets each follows the owner's Settings.
 */
@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

	private final PayrollService payroll;
	private final PermissionService perms;
	private final SessionService sessions;

	public PayrollController(PayrollService payroll, PermissionService perms, SessionService sessions) {
		this.payroll = payroll;
		this.perms = perms;
		this.sessions = sessions;
	}

	@GetMapping("/periods")
	public PayPeriods periods(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User user = sessions.requireUser(token);
		if (!perms.has(user, Permission.PAYROLL_NOTES)) perms.require(user, Permission.VIEW_PAYROLL, "No payroll access");
		return payroll.periods();
	}

	@GetMapping("/employees")
	public List<Person> employees(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		notesUser(token);
		return payroll.employees();
	}

	@GetMapping("/notes")
	public List<PayrollNoteView> notes(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate checkDate) {
		notesUser(token);
		return payroll.notesFor(checkDate);
	}

	@PostMapping("/notes")
	public ResponseEntity<PayrollNoteView> add(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PayrollNoteInput input) {
		return ResponseEntity.status(HttpStatus.CREATED).body(payroll.add(notesUser(token), input));
	}

	@PutMapping("/notes/{id}")
	public PayrollNoteView update(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody PayrollNoteInput input) {
		return payroll.update(notesUser(token), id, input);
	}

	@DeleteMapping("/notes/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		notesUser(token);
		payroll.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/summary")
	public PayrollSummary summary(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate checkDate) {
		payrollUser(token);
		return payroll.summary(checkDate);
	}

	@PutMapping("/notes/{id}/acknowledge")
	public PayrollNoteView acknowledge(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return payroll.acknowledge(payrollUser(token), id);
	}

	@PutMapping("/notes/{id}/finish")
	public PayrollNoteView finish(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return payroll.finish(payrollUser(token), id);
	}

	@PutMapping("/notes/{id}/reopen")
	public PayrollNoteView reopen(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		payrollUser(token);
		return payroll.reopen(id);
	}

	@GetMapping("/report")
	public PayrollReport report(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate checkDate) {
		payrollUser(token);
		return payroll.report(checkDate);
	}

	/** Pull one of the period's weeks from Salon Ultimate again (e.g. after closing an open timecard). */
	@PostMapping("/pull")
	public StatsImportView pull(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam LocalDate week) {
		payrollUser(token);
		return payroll.pullWeek(week);
	}

	@PutMapping("/pto/{id}/finish")
	public ResponseEntity<Void> finishPto(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		payroll.finishPto(payrollUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/pto/{id}/reopen")
	public ResponseEntity<Void> reopenPto(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		payrollUser(token);
		payroll.reopenPto(id);
		return ResponseEntity.noContent().build();
	}

	private User payrollUser(String token) {
		User user = sessions.requireUser(token);
		perms.require(user, Permission.VIEW_PAYROLL, "No access to the Payroll tab");
		return user;
	}

	private User notesUser(String token) {
		User user = sessions.requireUser(token);
		perms.require(user, Permission.PAYROLL_NOTES, "No access to payroll notes");
		return user;
	}
}
