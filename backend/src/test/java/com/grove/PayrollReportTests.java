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
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		matrix.saveAll(List.of(new PayMatrixRow(new BigDecimal("39"), new BigDecimal("16.50")),
			new PayMatrixRow(new BigDecimal("45"), new BigDecimal("17.25"))));
	}

	@Test
	void reportShowsBothWeeksWithRatesCommissionAndTheLeadBump() throws Exception {
		// Ava: 40 PPH both weeks, retail $80 in week 1 (commission), $20 and low PPG in week 2 (none).
		stats.save(stat(week1, "Ava Stone", "10", "400", "400", "80", "1.00", "25.50"));
		stats.save(stat(week2, "Ava Stone", "10", "400", "400", "20", "0.50", "10"));
		// Lena is a lead on the site with no pay code: matrix + $0.50.
		stats.save(stat(week1, "Lena Park", "10", "450", "450", "0", "1.60", "0"));
		signUp("lena@grove.test", "Lena", "Park");
		var lena = users.findByEmail("lena@grove.test").orElseThrow();
		lena.setRoles(Role.LEAD);
		lena.setSalonUltimateName("Lena Park");
		users.save(lena);
		codes.save(new PayCode("Ava Stone", 2, new BigDecimal("0.50"), null));

		// Max is a manager with no code: matrix + $1.00; a flat rate gets no bump.
		stats.save(stat(week1, "Max Hill", "10", "450", "450", "0", "0", "0"));
		signUp("max@grove.test", "Max", "Hill");
		var max = users.findByEmail("max@grove.test").orElseThrow();
		max.setRoles(Role.MANAGER);
		max.setSalonUltimateName("Max Hill");
		users.save(max);
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].name").value("Hill, Max"))
			.andExpect(jsonPath("$.rows[0].role").value("MANAGER"))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(18.25));
		codes.save(new PayCode("Max Hill", 15, null, new BigDecimal("20")));
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(20.00));
		stats.deleteAll(stats.findByWeekStart(week1).stream().filter(s -> s.getSuName().equals("Max Hill")).toList());

		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratesLoaded").value(true))
			.andExpect(jsonPath("$.weeks[0].weekStart").value(week1.toString()))
			.andExpect(jsonPath("$.weeks[1].weekStart").value(week2.toString()))
			.andExpect(jsonPath("$.rows.length()").value(2))
			.andExpect(jsonPath("$.rows[0].name").value("Park, Lena"))
			.andExpect(jsonPath("$.rows[0].role").value("LEAD"))
			.andExpect(jsonPath("$.rows[0].hasCode").value(false))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(17.75))
			// Under $60 retail but PPG at least 1.50 still earns commission (10% of $0).
			.andExpect(jsonPath("$.rows[0].week1.commission").value(0))
			.andExpect(jsonPath("$.rows[0].week2").doesNotExist())
			.andExpect(jsonPath("$.rows[1].name").value("Stone, Ava"))
			.andExpect(jsonPath("$.rows[1].role").doesNotExist())
			.andExpect(jsonPath("$.rows[1].week1.rate").value(17.00))
			.andExpect(jsonPath("$.rows[1].week1.commission").value(8.00))
			.andExpect(jsonPath("$.rows[1].week1.tips").value(25.5))
			.andExpect(jsonPath("$.rows[1].week2.commission").value(0))
			.andExpect(jsonPath("$.floorHours").value(30))
			.andExpect(jsonPath("$.commission").value(8.00));
	}

	@Test
	void theOwnerSetsIndividualRatesAndTheLeadBump() throws Exception {
		stats.save(stat(week1, "Ava Stone", "10", "400", "400", "0", "0", "0"));

		// Ava shows up on the pay table from Salon Ultimate even without a code.
		mvc.perform(get("/api/owner/pay-rules").cookie(owner))
			.andExpect(jsonPath("$.leadBump").value(0.5))
			.andExpect(jsonPath("$.managerBump").value(1.0))
			.andExpect(jsonPath("$.people[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.people[0].hasCode").value(false));

		mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ava Stone\",\"code\":15,\"flatRate\":21}"))
			.andExpect(jsonPath("$.people[0].flatRate").value(21));
		mvc.perform(get("/api/payroll/report").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.rows[0].week1.rate").value(21.00));

		// Back to the matrix with a premium.
		mvc.perform(put("/api/owner/pay-rules/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ava stone\",\"code\":3,\"premium\":0.75}"))
			.andExpect(jsonPath("$.people[0].flatRate").doesNotExist())
			.andExpect(jsonPath("$.people[0].premium").value(0.75));
		assertThat(codes.findAll()).hasSize(1);

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

		mvc.perform(delete("/api/owner/pay-rules/people").param("name", "Ava Stone").cookie(owner))
			.andExpect(jsonPath("$.people[0].hasCode").value(false));
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
