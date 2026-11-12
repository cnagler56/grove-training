package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.grove.Domain.PayCode;
import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.Role;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.PayCodeRepository;
import com.grove.Repository.PayMatrixRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Service.PayrollService;
import com.grove.Service.SessionService;

/** The Payroll tab's hours-and-pay report, built from the weekly Salon Ultimate pulls. Names are made up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollReportTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired WeeklyStatRepository stats;
	@Autowired PayMatrixRepository matrix;
	@Autowired PayCodeRepository codes;
	@Autowired AppSettingRepository settings;
	@Autowired com.grove.Repository.VacationPayRequestRepository vacationPay;
	@Autowired com.grove.Repository.PayrollNoteRepository payrollNotes;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private final LocalDate check = LocalDate.of(2026, 11, 13);
	private final LocalDate week1 = LocalDate.of(2026, 10, 24);
	private final LocalDate week2 = LocalDate.of(2026, 10, 31);

	@BeforeEach
	void setUp() throws Exception {
		stats.deleteAll();
		payrollNotes.deleteAll();
		vacationPay.deleteAll();
		matrix.deleteAll();
		codes.deleteAll();
		settings.findById("pay.leadBump").ifPresent(settings::delete);
		settings.findById("pay.managerBump").ifPresent(settings::delete);
		settings.findById("pay.leadProdBump").ifPresent(settings::delete);
		settings.findById("pay.minimumRate").ifPresent(settings::delete);
		settings.findById("pay.receptionistRate").ifPresent(settings::delete);
		settings.findAll().stream().filter(x -> x.getKey().startsWith("pay.removed.")).forEach(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		matrix.saveAll(List.of(new PayMatrixRow(new BigDecimal("39"), new BigDecimal("16.50")),
			new PayMatrixRow(new BigDecimal("45"), new BigDecimal("17.25"))));
	}

	@Test
	void ratesFollowTheMinimumRoleBumpsAndFlatRates() throws Exception {
		// Matrix: $16.50 from 39 PPH, $17.25 from 45 PPH. Minimum $16, lead +$0.50, manager +$1.00.
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		person("lena@grove.test", "Lena", "Park", Role.LEAD);
		person("max@grove.test", "Max", "Hill", Role.MANAGER);
		person("dee@grove.test", "Dee", "Desk", Role.RECEPTIONIST);
		// Ava: 40 PPH both weeks; retail $80 in week 1 (commission), $20 with low PPG in week 2 (none).
		stats.save(stat(week1, "Ava Stone", "10", "400", "400", "80", "1.00", "25.50"));
		stats.save(stat(week2, "Ava Stone", "10", "400", "400", "20", "0.50", "10"));
		stats.save(stat(week1, "Lena Park", "10", "450", "450", "0", "1.60", "0"));
		stats.save(stat(week1, "Max Hill", "10", "450", "450", "0", "0", "0"));
		stats.save(stat(week1, "Dee Desk", "10", "0", "0", "0", "0", "0"));
		// Cara has no site account: paid like a stylist, at the minimum for 30 PPH, and flagged.
		stats.save(stat(week1, "Cara Diaz", "10", "300", "300", "0", "0", "0"));
		// An old premium from the codes file no longer counts.
		codes.save(new PayCode("Ava Stone", 2, new BigDecimal("0.50"), null));

		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratesLoaded").value(true))
			.andExpect(jsonPath("$.weeks[0].weekStart").value(week1.toString()))
			.andExpect(jsonPath("$.weeks[1].weekStart").value(week2.toString()))
			.andExpect(jsonPath("$.rows.length()").value(5))
			.andExpect(jsonPath("$.rows[0].name").value("Desk, Dee"))
			// Receptionists get the receptionist rate ($13 to start), never the matrix.
			.andExpect(jsonPath("$.rows[0].week1.rate").value(13.00))
			.andExpect(jsonPath("$.rows[0].problem").doesNotExist())
			.andExpect(jsonPath("$.rows[1].name").value("Diaz, Cara"))
			.andExpect(jsonPath("$.rows[1].week1.rate").value(16.00))
			.andExpect(jsonPath("$.rows[1].problem").value(org.hamcrest.Matchers.startsWith("Not linked")))
			.andExpect(jsonPath("$.rows[2].name").value("Hill, Max"))
			.andExpect(jsonPath("$.rows[2].role").value("MANAGER"))
			.andExpect(jsonPath("$.rows[2].week1.rate").value(18.25))
			.andExpect(jsonPath("$.rows[3].name").value("Park, Lena"))
			.andExpect(jsonPath("$.rows[3].role").value("LEAD"))
			.andExpect(jsonPath("$.rows[3].week1.rate").value(17.75))
			// Under $60 retail but PPG at least 1.50 still earns commission (10% of $0).
			.andExpect(jsonPath("$.rows[3].week1.commission").value(0))
			.andExpect(jsonPath("$.rows[3].week2").doesNotExist())
			.andExpect(jsonPath("$.rows[4].name").value("Stone, Ava"))
			.andExpect(jsonPath("$.rows[4].problem").doesNotExist())
			.andExpect(jsonPath("$.rows[4].week1.rate").value(16.50))
			.andExpect(jsonPath("$.rows[4].week1.commission").value(8.00))
			.andExpect(jsonPath("$.rows[4].week1.tips").value(25.5))
			.andExpect(jsonPath("$.rows[4].week2.commission").value(0))
			.andExpect(jsonPath("$.floorHours").value(60))
			.andExpect(jsonPath("$.commission").value(8.00));

		// Personal base rates: a receptionist gets exactly theirs; a manager's replaces the minimum
		// under the matrix, and the bump still goes on top ($20 + $1, since $17.25 is lower).
		flat("Dee Desk", "14");
		flat("Max Hill", "20");
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(14.00))
			.andExpect(jsonPath("$.rows[2].week1.rate").value(21.00));
		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			// People are listed by last name: Desk, Diaz, Hill, Park, Stone.
			.andExpect(jsonPath("$.people[0].ownRate").value(14))
			.andExpect(jsonPath("$.people[0].defaultRate").value(13))
			.andExpect(jsonPath("$.people[0].paidRate").value(14))
			.andExpect(jsonPath("$.people[2].paidRate").value(21))
			.andExpect(jsonPath("$.people[3].ownRate").doesNotExist())
			.andExpect(jsonPath("$.people[3].paidRate").value(16.5));
	}

	@Test
	void theOwnerSetsTheMinimumBumpsAndFlatRates() throws Exception {
		stats.save(stat(week1, "Ava Stone", "10", "300", "300", "0", "0", "0"));

		// Ava shows up on the pay table from Salon Ultimate, paid on the matrix.
		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			.andExpect(jsonPath("$.minimumRate").value(16.0))
			.andExpect(jsonPath("$.leadBump").value(0.5))
			.andExpect(jsonPath("$.managerBump").value(1.0))
			.andExpect(jsonPath("$.receptionistRate").value(13.0))
			.andExpect(jsonPath("$.people[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.people[0].ownRate").doesNotExist())
			.andExpect(jsonPath("$.people[0].paidRate").value(16.0));

		// Raising the minimum raises anyone under it.
		mvc.perform(put("/api/owner/pay-rules/minimum").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":17}"))
			.andExpect(jsonPath("$.minimumRate").value(17.0));
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(17.00));
		mvc.perform(put("/api/owner/pay-rules/minimum").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":0}"))
			.andExpect(status().isBadRequest());

		mvc.perform(put("/api/owner/pay-rules/receptionist-rate").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":13.5}"))
			.andExpect(jsonPath("$.receptionistRate").value(13.5));

		// Her own base rate, then back to the default (case doesn't matter, and an old code row is cleared too).
		codes.save(new PayCode("Ava Stone", 2, new BigDecimal("0.50"), null));
		flat("Ava Stone", "21").andExpect(jsonPath("$.people[0].ownRate").value(21));
		assertThat(codes.findAll()).hasSize(1);
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(21.00));
		mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ava stone\",\"baseRate\":null}"))
			.andExpect(jsonPath("$.people[0].ownRate").doesNotExist());
		assertThat(codes.findAll()).isEmpty();
		flat("Ava Stone", "-1").andExpect(status().isBadRequest());
		// Setting it to the default is the same as using the default.
		flat("Ava Stone", "17").andExpect(jsonPath("$.people[0].ownRate").doesNotExist());

		mvc.perform(put("/api/owner/pay-rules/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"LEAD\",\"amount\":0.75}"))
			.andExpect(jsonPath("$.leadBump").value(0.75));
		mvc.perform(put("/api/owner/pay-rules/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"MANAGER\",\"amount\":1.25}"))
			.andExpect(jsonPath("$.managerBump").value(1.25));
		mvc.perform(put("/api/owner/pay-rules/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"LEAD\",\"amount\":-1}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/owner/pay-rules/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"STYLIST\",\"amount\":1}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theEntryWindowPutsHoursPaidTimeOffAndNotesTogetherPerPerson() throws Exception {
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		person("lena@grove.test", "Lena", "Park", Role.LEAD);
		stats.save(stat(week1, "Ava Stone", "10", "400", "400", "80", "1.00", "25.50"));
		stats.save(stat(week2, "Ava Stone", "10", "400", "400", "0", "0", "10"));
		Long avaId = users.findByEmail("ava@grove.test").orElseThrow().getUserId();
		Long lenaId = users.findByEmail("lena@grove.test").orElseThrow().getUserId();
		// Ava: approved paid time off and a deduction. Lena: no hours, just a note.
		var pto = new com.grove.Domain.VacationPayRequest();
		pto.setUserId(avaId);
		pto.setHours(new BigDecimal("8"));
		pto.setCheckDate(check);
		pto.setStatus(com.grove.Domain.VacationPayRequest.Status.APPROVED);
		vacationPay.save(pto);
		note(avaId, "DEDUCTION", "25.50", "Uniform shirt");
		note(lenaId, "COMMENT", null, "New address");

		mvc.perform(get("/api/payroll/entry").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.checkDate").value(check.toString()))
			.andExpect(jsonPath("$.people.length()").value(2))
			.andExpect(jsonPath("$.people[0].name").value("Park, Lena"))
			.andExpect(jsonPath("$.people[0].week1").doesNotExist())
			.andExpect(jsonPath("$.people[0].problem").value(org.hamcrest.Matchers.startsWith("No hours")))
			.andExpect(jsonPath("$.people[0].notes[0].note").value("New address"))
			.andExpect(jsonPath("$.people[1].name").value("Stone, Ava"))
			.andExpect(jsonPath("$.people[1].week1.rate").value(16.50))
			.andExpect(jsonPath("$.people[1].week2.tips").value(10))
			.andExpect(jsonPath("$.people[1].ptoHours").value(8))
			.andExpect(jsonPath("$.people[1].deductions").value(25.5));

		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		mvc.perform(get("/api/payroll/entry").param("checkDate", check.toString()).cookie(gm)).andExpect(status().isForbidden());
	}

	private void note(Long userId, String kind, String amount, String text) throws Exception {
		mvc.perform(post("/api/payroll/notes").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + userId + ",\"checkDate\":\"" + check + "\",\"kind\":\"" + kind + "\",\"amount\":" + amount
					+ ",\"note\":\"" + text + "\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void eachPersonsBumpCanBeChosenOrTheirOwnAmount() throws Exception {
		// Matrix: $16.50 from 39 PPH. Ava is a stylist at 40 PPH, so $16.50 with no bump.
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		person("lena@grove.test", "Lena", "Park", Role.LEAD);
		stats.save(stat(week1, "Ava Stone", "10", "400", "400", "0", "0", "0"));
		stats.save(stat(week1, "Lena Park", "10", "400", "400", "0", "0", "0"));

		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			.andExpect(jsonPath("$.leadProdBump").value(0.75))
			// Lena follows her role: the $0.50 lead bump.
			.andExpect(jsonPath("$.people[0].name").value("Lena Park"))
			.andExpect(jsonPath("$.people[0].bumpKind").doesNotExist())
			.andExpect(jsonPath("$.people[0].bump").value(0.5));

		// Lead/prod for Lena, her own $1.50 for Ava.
		bumpFor("Lena Park", "LEAD_PROD", null).andExpect(jsonPath("$.people[0].bump").value(0.75))
			.andExpect(jsonPath("$.people[0].paidRate").value(16.75));
		bumpFor("Ava Stone", "CUSTOM", "1.50").andExpect(jsonPath("$.people[1].bump").value(1.5))
			.andExpect(jsonPath("$.people[1].paidRate").value(17.5));
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(17.25))
			.andExpect(jsonPath("$.rows[1].week1.rate").value(18.00));

		// Changing the lead/prod amount moves everyone on it; a base rate keeps the bump.
		mvc.perform(put("/api/owner/pay-rules/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"LEAD_PROD\",\"amount\":1}"))
			.andExpect(jsonPath("$.people[0].bump").value(1.0));
		flat("Ava Stone", "17").andExpect(jsonPath("$.people[1].bumpKind").value("CUSTOM"))
			.andExpect(jsonPath("$.people[1].paidRate").value(18.5));

		// Back to her role, and the custom amount has to make sense.
		bumpFor("Ava Stone", "ROLE", null).andExpect(jsonPath("$.people[1].bump").value(0))
			.andExpect(jsonPath("$.people[1].ownRate").value(17));
		bumpFor("Ava Stone", "CUSTOM", "-1").andExpect(status().isBadRequest());
		bumpFor("Ava Stone", "BONUS", null).andExpect(status().isBadRequest());
		// None turns off even a role bump.
		bumpFor("Lena Park", "NONE", null).andExpect(jsonPath("$.people[0].bump").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions bumpFor(String name, String kind, String amount) throws Exception {
		return mvc.perform(put("/api/owner/pay-rules/people/bump").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"amount\":" + amount + "}"));
	}

	@Test
	void removingSomeoneHidesThemUntilTheyShowUpAgain() throws Exception {
		stats.save(stat(week1, "Ava Stone", "10", "300", "300", "0", "0", "0"));
		stats.save(stat(week1, "Ben Cole", "10", "300", "300", "0", "0", "0"));
		flat("Ava Stone", "18");

		mvc.perform(delete("/api/owner/pay-rules/people").param("name", "ava stone").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.people.length()").value(1))
			.andExpect(jsonPath("$.people[0].name").value("Ben Cole"));
		// Her own rate went with her.
		assertThat(codes.findAll()).isEmpty();
		// Her past hours still count on the Payroll tab.
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows.length()").value(2));

		// A week that starts after today brings her back.
		stats.save(stat(LocalDate.now().plusWeeks(1), "Ava Stone", "10", "300", "300", "0", "0", "0"));
		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			.andExpect(jsonPath("$.people.length()").value(2));

		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		mvc.perform(delete("/api/owner/pay-rules/people").param("name", "Ben Cole").cookie(gm)).andExpect(status().isForbidden());
	}

	private void person(String email, String first, String last, Role role) throws Exception {
		signUp(email, first, last);
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		u.setSalonUltimateName(first + " " + last);
		users.save(u);
	}

	private org.springframework.test.web.servlet.ResultActions flat(String name, String rate) throws Exception {
		return mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"baseRate\":" + rate + "}"));
	}

	@Test
	void onlyPayrollTabPeopleSeeTheReportAndOnlyOwnersChangeRates() throws Exception {
		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		var u = users.findByEmail("gm@grove.test").orElseThrow();
		u.setRoles(Role.GENERAL_MANAGER);
		users.save(u);
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(post("/api/payroll/pull").param("week", week1.toString()).cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(put("/api/owner/pay-rules/bump").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"LEAD\",\"amount\":1}"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/payroll/report").param("checkDate", check.plusDays(1).toString()).cookie(owner))
			.andExpect(status().isBadRequest());
		assertThat(PayrollService.period(check).workStart()).isEqualTo(week1);
	}

	private static WeeklyStat stat(LocalDate week, String name, String hours, String gross, String net, String retail, String ppg, String tips) {
		WeeklyStat s = new WeeklyStat();
		s.setWeekStart(week);
		s.setSuName(name);
		s.setFloorHours(new BigDecimal(hours));
		s.setOffFloorHours(new BigDecimal("1.5"));
		s.setServiceSales(new BigDecimal(gross));
		s.setNetServiceSales(new BigDecimal(net));
		s.setRetailSales(new BigDecimal(retail));
		s.setProductPerGuest(new BigDecimal(ppg));
		s.setTips(new BigDecimal(tips));
		return s;
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
