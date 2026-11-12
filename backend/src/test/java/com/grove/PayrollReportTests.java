package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
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
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private final LocalDate check = LocalDate.of(2026, 11, 13);
	private final LocalDate week1 = LocalDate.of(2026, 10, 24);
	private final LocalDate week2 = LocalDate.of(2026, 10, 31);

	@BeforeEach
	void setUp() throws Exception {
		stats.deleteAll();
		matrix.deleteAll();
		codes.deleteAll();
		settings.findById("pay.leadBump").ifPresent(settings::delete);
		settings.findById("pay.managerBump").ifPresent(settings::delete);
		settings.findById("pay.minimumRate").ifPresent(settings::delete);
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
			.andExpect(jsonPath("$.rows[0].week1.rate").doesNotExist())
			.andExpect(jsonPath("$.rows[0].problem").value(org.hamcrest.Matchers.startsWith("Receptionist with no flat rate")))
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

		// Flat rates: exactly that, no bump, and the receptionist's flag goes away.
		flat("Dee Desk", "14");
		flat("Max Hill", "20");
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(14.00))
			.andExpect(jsonPath("$.rows[0].flatRate").value(14))
			.andExpect(jsonPath("$.rows[0].problem").doesNotExist())
			.andExpect(jsonPath("$.rows[2].week1.rate").value(20.00));
	}

	@Test
	void theOwnerSetsTheMinimumBumpsAndFlatRates() throws Exception {
		stats.save(stat(week1, "Ava Stone", "10", "300", "300", "0", "0", "0"));

		// Ava shows up on the pay table from Salon Ultimate, paid on the matrix.
		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			.andExpect(jsonPath("$.minimumRate").value(16.0))
			.andExpect(jsonPath("$.leadBump").value(0.5))
			.andExpect(jsonPath("$.managerBump").value(1.0))
			.andExpect(jsonPath("$.people[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.people[0].flatRate").doesNotExist());

		// Raising the minimum raises anyone under it.
		mvc.perform(put("/api/owner/pay-rules/minimum").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":17}"))
			.andExpect(jsonPath("$.minimumRate").value(17.0));
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(17.00));
		mvc.perform(put("/api/owner/pay-rules/minimum").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":0}"))
			.andExpect(status().isBadRequest());

		// A flat rate, then back to the matrix (case doesn't matter, and an old code row is cleared too).
		codes.save(new PayCode("Ava Stone", 2, new BigDecimal("0.50"), null));
		flat("Ava Stone", "21").andExpect(jsonPath("$.people[0].flatRate").value(21));
		assertThat(codes.findAll()).hasSize(1);
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(21.00));
		mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ava stone\",\"flatRate\":null}"))
			.andExpect(jsonPath("$.people[0].flatRate").doesNotExist());
		assertThat(codes.findAll()).isEmpty();
		flat("Ava Stone", "-1").andExpect(status().isBadRequest());

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

	private void person(String email, String first, String last, Role role) throws Exception {
		signUp(email, first, last);
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		u.setSalonUltimateName(first + " " + last);
		users.save(u);
	}

	private org.springframework.test.web.servlet.ResultActions flat(String name, String rate) throws Exception {
		return mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"flatRate\":" + rate + "}"));
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
