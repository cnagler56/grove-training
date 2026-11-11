package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.grove.Domain.Permission;
import com.grove.Domain.Role;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.PayrollNoteRepository;
import com.grove.Repository.UserNotificationRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.VacationPayRequestRepository;
import com.grove.Service.PayrollService;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired PayrollNoteRepository notes;
	@Autowired VacationPayRequestRepository vacationPay;
	@Autowired AppSettingRepository settings;
	@Autowired UserNotificationRepository inbox;
	@Autowired PayrollService service;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private Cookie gm;
	private Cookie ava;
	private Long avaId;
	private LocalDate check;

	@BeforeEach
	void setUp() throws Exception {
		notes.deleteAll();
		inbox.deleteAll();
		vacationPay.deleteAll();
		settings.findById(Permission.PAYROLL_NOTES.key()).ifPresent(settings::delete);
		settings.findById(Permission.VIEW_PAYROLL.key()).ifPresent(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		gm = signUp("gm@grove.test", "Gia", "Ray");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		ava = signUp("ava@grove.test", "Ava", "Stone");
		avaId = users.findByEmail("ava@grove.test").orElseThrow().getUserId();
		check = service.periods().defaultCheckDate();
	}

	@Test
	void periodsAreKeyedByCheckDateAndDefaultToTheLatestFinishedOne() {
		// The Nov 13 check pays Sat Oct 24 to Fri Nov 6.
		var p = PayrollService.period(LocalDate.of(2026, 11, 13));
		assertThat(p.workStart()).isEqualTo(LocalDate.of(2026, 10, 24));
		assertThat(p.workEnd()).isEqualTo(LocalDate.of(2026, 11, 6));

		// That period is the default from the Saturday after it ends until the next one ends.
		assertThat(PayrollService.defaultCheckDate(LocalDate.of(2026, 11, 6))).isEqualTo(LocalDate.of(2026, 10, 30));
		assertThat(PayrollService.defaultCheckDate(LocalDate.of(2026, 11, 7))).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(PayrollService.defaultCheckDate(LocalDate.of(2026, 11, 11))).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(PayrollService.defaultCheckDate(LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(PayrollService.defaultCheckDate(LocalDate.of(2026, 11, 21))).isEqualTo(LocalDate.of(2026, 11, 27));

		var periods = PayrollService.periodsAround(LocalDate.of(2026, 11, 11));
		assertThat(periods.periods()).extracting(x -> x.checkDate()).contains(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 10, 30));
		assertThat(periods.periods().get(0).checkDate()).isAfter(periods.periods().get(1).checkDate());
		assertThat(PayrollService.isCheckDate(LocalDate.of(2026, 10, 30))).isTrue();
		assertThat(PayrollService.isCheckDate(LocalDate.of(2026, 11, 12))).isFalse();
	}

	@Test
	void ownersAndGeneralManagersAddNotesThatShowOnThePayrollTab() throws Exception {
		long deduction = add(gm, "DEDUCTION", "25.50", "Uniform shirt").andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Ava Stone"))
			.andExpect(jsonPath("$.createdByName").value("Gia Ray"))
			.andReturn().getResponse().getContentAsString().transform(PayrollTests::id);
		add(owner, "ADDITION", "40", "Covered the front desk").andExpect(status().isCreated());
		add(owner, "COMMENT", null, "Ask about her new address").andExpect(status().isCreated());

		mvc.perform(get("/api/payroll/notes").param("checkDate", check.toString()).cookie(gm))
			.andExpect(jsonPath("$.length()").value(3));

		// Approved paid time off for the same check shows up; pending is counted separately.
		ptoRequest("8");
		long pending = vacationPay.findAll().get(0).getId();
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.employees.length()").value(1))
			.andExpect(jsonPath("$.employees[0].pendingPtoHours").value(8))
			.andExpect(jsonPath("$.pendingPtoRequests").value(1));
		mvc.perform(put("/api/vacation-pay/" + pending + "/approve").cookie(owner)).andExpect(status().isOk());

		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.period.checkDate").value(check.toString()))
			.andExpect(jsonPath("$.employees[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.employees[0].ptoHours").value(8))
			.andExpect(jsonPath("$.employees[0].additions").value(40))
			.andExpect(jsonPath("$.employees[0].deductions").value(25.5))
			.andExpect(jsonPath("$.employees[0].notes.length()").value(3))
			.andExpect(jsonPath("$.pendingPtoRequests").value(0));

		// Edit and delete.
		mvc.perform(put("/api/payroll/notes/" + deduction).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content(json(avaId, check, "DEDUCTION", "30", "Two uniform shirts")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(30))
			.andExpect(jsonPath("$.updatedByName").value("Owner Person"));
		mvc.perform(delete("/api/payroll/notes/" + deduction).cookie(gm)).andExpect(status().isNoContent());
		assertThat(notes.findAll()).hasSize(2);

		// Another period is empty.
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.minusDays(14).toString()).cookie(owner))
			.andExpect(jsonPath("$.employees.length()").value(0));
	}

	@Test
	void accessFollowsSettings() throws Exception {
		// By default only owners see the Payroll tab, and staff can't add notes.
		mvc.perform(get("/api/me/permissions").cookie(gm))
			.andExpect(jsonPath("$.payrollNotes").value(true))
			.andExpect(jsonPath("$.viewPayroll").value(false));
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(gm)).andExpect(status().isForbidden());
		add(ava, "COMMENT", null, "hi").andExpect(status().isForbidden());
		mvc.perform(get("/api/payroll/periods").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(get("/api/payroll/periods")).andExpect(status().isUnauthorized());

		// The owner hands out the Payroll tab and takes away notes.
		mvc.perform(put("/api/owner/permissions").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":{\"VIEW_PAYROLL\":[\"GENERAL_MANAGER\"],\"PAYROLL_NOTES\":[]}}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(gm)).andExpect(status().isOk());
		add(gm, "COMMENT", null, "hi").andExpect(status().isForbidden());
		mvc.perform(get("/api/payroll/periods").cookie(gm)).andExpect(status().isOk());
		add(owner, "COMMENT", null, "hi").andExpect(status().isCreated());
	}

	@Test
	void theOwnerAcknowledgesThenFinishesANoteAndTheAuthorIsTold() throws Exception {
		long id = id(add(gm, "DEDUCTION", "25.50", "Uniform shirt").andExpect(jsonPath("$.status").value("NEW"))
			.andReturn().getResponse().getContentAsString());
		Long gmId = users.findByEmail("gm@grove.test").orElseThrow().getUserId();
		Long ownerId = users.findByEmail("owner@grove.test").orElseThrow().getUserId();
		assertThat(inbox.findAll()).anyMatch(n -> n.getUserId().equals(ownerId) && n.getTitle().equals("New payroll note"));

		// Finished needs an acknowledgment first, and only Payroll tab people can do either.
		mvc.perform(put("/api/payroll/notes/" + id + "/finish").cookie(owner)).andExpect(status().isBadRequest());
		mvc.perform(put("/api/payroll/notes/" + id + "/acknowledge").cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(put("/api/payroll/notes/" + id + "/acknowledge").cookie(owner))
			.andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
			.andExpect(jsonPath("$.acknowledgedByName").value("Owner Person"));
		assertThat(inbox.findAll()).anyMatch(n -> n.getUserId().equals(gmId) && n.getTitle().equals("Payroll note acknowledged"));
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.openNotes").value(1));

		mvc.perform(put("/api/payroll/notes/" + id + "/finish").cookie(owner))
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.finishedByName").value("Owner Person"));
		assertThat(inbox.findAll()).anyMatch(n -> n.getUserId().equals(gmId) && n.getTitle().equals("Payroll note finished"));
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.openNotes").value(0))
			.andExpect(jsonPath("$.deductions").value(25.5));

		// Closed: it can't be changed or deleted until it's reopened.
		mvc.perform(put("/api/payroll/notes/" + id).cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content(json(avaId, check, "DEDUCTION", "30", null)))
			.andExpect(status().isConflict());
		mvc.perform(delete("/api/payroll/notes/" + id).cookie(gm)).andExpect(status().isConflict());
		mvc.perform(put("/api/payroll/notes/" + id + "/reopen").cookie(owner)).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

		// Changing an acknowledged note sends it back to be acknowledged again.
		mvc.perform(put("/api/payroll/notes/" + id).cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content(json(avaId, check, "DEDUCTION", "30", null)))
			.andExpect(jsonPath("$.status").value("NEW"));
		assertThat(inbox.findAll()).anyMatch(n -> n.getUserId().equals(ownerId) && n.getTitle().equals("Payroll note changed"));
	}

	@Test
	void approvedPaidTimeOffIsMarkedFinishedAndTheEmployeeIsTold() throws Exception {
		ptoRequest("8");
		long id = vacationPay.findAll().get(0).getId();
		// Only approved requests can be finished.
		mvc.perform(put("/api/payroll/pto/" + id + "/finish").cookie(owner)).andExpect(status().isBadRequest());
		mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(owner)).andExpect(status().isOk());
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.employees[0].pto[0].id").value(id))
			.andExpect(jsonPath("$.employees[0].pto[0].status").value("APPROVED"))
			.andExpect(jsonPath("$.openPto").value(1));

		mvc.perform(put("/api/payroll/pto/" + id + "/finish").cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(put("/api/payroll/pto/" + id + "/finish").cookie(owner)).andExpect(status().isNoContent());
		assertThat(inbox.findAll()).anyMatch(n -> n.getUserId().equals(avaId) && n.getTitle().equals("Paid time off entered into payroll"));
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.employees[0].pto[0].finishedByName").value("Owner Person"))
			.andExpect(jsonPath("$.employees[0].ptoHours").value(8))
			.andExpect(jsonPath("$.openPto").value(0));
		mvc.perform(get("/api/vacation-pay/mine").cookie(ava)).andExpect(jsonPath("$[0].finishedByName").value("Owner Person"));

		// Closed: the employee can't cancel it and the owner can't undo the approval until it's reopened.
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(ava)).andExpect(status().isBadRequest());
		mvc.perform(put("/api/vacation-pay/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"No\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/payroll/pto/" + id + "/reopen").cookie(owner)).andExpect(status().isNoContent());
		mvc.perform(get("/api/payroll/summary").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.openPto").value(1));
	}

	@Test
	void notesAreChecked() throws Exception {
		add(owner, "DEDUCTION", null, "No amount").andExpect(status().isBadRequest());
		add(owner, "ADDITION", "-5", null).andExpect(status().isBadRequest());
		add(owner, "ADDITION", "1.234", null).andExpect(status().isBadRequest());
		add(owner, "COMMENT", null, " ").andExpect(status().isBadRequest());
		add(owner, "BONUS", "5", null).andExpect(status().isBadRequest());
		mvc.perform(post("/api/payroll/notes").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content(json(avaId, check.plusDays(1), "ADDITION", "5", null)))
			.andExpect(status().isBadRequest());
		Long ownerId = users.findByEmail("owner@grove.test").orElseThrow().getUserId();
		mvc.perform(post("/api/payroll/notes").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content(json(ownerId, check, "ADDITION", "5", null)))
			.andExpect(status().isBadRequest());
		// A comment's amount is dropped.
		add(owner, "COMMENT", "5", "Note").andExpect(jsonPath("$.amount").doesNotExist());
	}

	@Test
	void deletingAnAccountRemovesTheirNotes() throws Exception {
		add(owner, "ADDITION", "5", null).andExpect(status().isCreated());
		mvc.perform(delete("/api/admin/team/" + avaId).cookie(owner)).andExpect(status().is2xxSuccessful());
		assertThat(notes.findAll()).isEmpty();
	}

	private ResultActions add(Cookie who, String kind, String amount, String note) throws Exception {
		return mvc.perform(post("/api/payroll/notes").cookie(who).contentType(MediaType.APPLICATION_JSON)
			.content(json(avaId, check, kind, amount, note)));
	}

	/** Ava asks for paid time off on the default period's check, if it's still open to requests. */
	private void ptoRequest(String hours) {
		var r = new com.grove.Domain.VacationPayRequest();
		r.setUserId(avaId);
		r.setHours(new java.math.BigDecimal(hours));
		r.setCheckDate(check);
		vacationPay.save(r);
	}

	private static String json(Long userId, LocalDate checkDate, String kind, String amount, String note) {
		return "{\"userId\":" + userId + ",\"checkDate\":\"" + checkDate + "\",\"kind\":\"" + kind + "\",\"amount\":" + amount
			+ ",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}";
	}

	private static long id(String body) {
		return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
	}

	private void setRole(String email, Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
