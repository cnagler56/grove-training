package com.grove;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

import com.grove.Domain.Role;
import com.grove.Repository.StatsImportRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Service.SalonUltimateClient;
import com.grove.Service.SalonUltimateReportTest;
import com.grove.Service.SessionService;
import com.grove.Service.StatsService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired WeeklyStatRepository stats;
	@Autowired StatsImportRepository imports;
	@Autowired StatsService statsService;
	@MockBean SalonUltimateClient salonUltimate;
	@MockBean JavaMailSender mailSender;

	@BeforeEach
	void clean() throws Exception {
		stats.deleteAll();
		imports.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		when(salonUltimate.isConfigured()).thenReturn(true);
		when(salonUltimate.weekReport(any(), any())).thenReturn(SalonUltimateReportTest.REPORT);
	}

	@Test
	void staffSeeOnlyTheirOwnWeeksAndManagersSeeEveryone() throws Exception {
		Cookie owner = signUp("owner@grove.test", "Owner", "Person");
		Cookie ava = signUp("ava@grove.test", "Ava", "Stone");
		Cookie mia = signUp("mia@grove.test", "Mia", "Cruz");
		LocalDate week = statsService.latestCompleteWeek();

		mvc.perform(post("/api/stats/import").param("week", week.toString()).cookie(ava))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/stats/import").param("week", week.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OK"))
			.andExpect(jsonPath("$.rowCount").value(2));

		// Ava's site name matches Salon Ultimate exactly, so she's linked automatically.
		mvc.perform(get("/api/stats/mine").cookie(ava))
			.andExpect(jsonPath("$.linked").value(true))
			.andExpect(jsonPath("$.weeks.length()").value(1))
			.andExpect(jsonPath("$.weeks[0].serviceSales").value(1200.00))
			.andExpect(jsonPath("$.weeks[0].serviceSalesPerHour").value(39.34));
		// Mia's isn't ("Mia Cruz" vs "Mia De La Cruz"), so she sees nothing until a manager links her.
		mvc.perform(get("/api/stats/mine").cookie(mia))
			.andExpect(jsonPath("$.linked").value(false))
			.andExpect(jsonPath("$.weeks.length()").value(0));
		mvc.perform(get("/api/stats/team").cookie(mia)).andExpect(status().isForbidden());

		Long miaId = users.findByEmail("mia@grove.test").orElseThrow().getUserId();
		mvc.perform(put("/api/admin/team/" + miaId + "/salon-ultimate").cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ava Stone\"}"))
			.andExpect(status().isConflict());
		mvc.perform(put("/api/admin/team/" + miaId + "/salon-ultimate").cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mia De La Cruz\"}"))
			.andExpect(jsonPath("$.salonUltimateName").value("Mia De La Cruz"));
		mvc.perform(get("/api/stats/mine").cookie(mia))
			.andExpect(jsonPath("$.weeks[0].serviceSales").value(400.00));

		mvc.perform(get("/api/stats/team").cookie(owner))
			.andExpect(jsonPath("$.weekStart").value(week.toString()))
			.andExpect(jsonPath("$.rows.length()").value(2))
			.andExpect(jsonPath("$.rows[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.lastImport.status").value("OK"));
	}

	@Test
	void openTimecardsAreReportedInsteadOfSavingNothing() throws Exception {
		Cookie owner = signUp("owner@grove.test", "Owner", "Person");
		when(salonUltimate.weekReport(any(), any())).thenReturn(
			"Could Not Display Report. There are unclosed time cards for the following employees: Ava Stone on 10/22/2026. Close them.");
		mvc.perform(post("/api/stats/import").param("week", statsService.latestCompleteWeek().toString()).cookie(owner))
			.andExpect(jsonPath("$.status").value("BLOCKED"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ava Stone on 10/22/2026")));
	}

	@Test
	void unfinishedWeeksCantBePulled() throws Exception {
		Cookie owner = signUp("owner@grove.test", "Owner", "Person");
		mvc.perform(post("/api/stats/import").param("week", statsService.latestCompleteWeek().plusWeeks(1).toString()).cookie(owner))
			.andExpect(status().isBadRequest());
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		Cookie c = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		if (email.startsWith("owner")) {
			var u = users.findByEmail(email).orElseThrow();
			u.setRoles(Role.OWNER);
			users.save(u);
		}
		return c;
	}
}
