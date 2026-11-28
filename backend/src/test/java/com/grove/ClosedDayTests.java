package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
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

import com.grove.Domain.Holiday;
import com.grove.Domain.Role;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.ClosedDayRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.SessionService;

/** Closed days: holidays the owner checks on Settings, and days marked on the time off calendar. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClosedDayTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired ClosedDayRepository closedDays;
	@Autowired AppSettingRepository settings;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private Cookie gm;
	private Cookie ava;

	@BeforeEach
	void setUp() throws Exception {
		clear();
		owner = signUp("owner@grove.test", "Owner", "Person");
		gm = signUp("gia@grove.test", "Gia", "Ray");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		var u = users.findByEmail("gia@grove.test").orElseThrow();
		u.setRoles(Role.GENERAL_MANAGER);
		users.save(u);
	}

	@AfterEach
	void tearDown() {
		clear();
	}

	@Test
	void holidaysFallOnTheRightDays() {
		assertThat(Holiday.EASTER.on(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
		assertThat(Holiday.EASTER.on(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
		assertThat(Holiday.EASTER.on(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
		assertThat(Holiday.MEMORIAL_DAY.on(2026)).isEqualTo(LocalDate.of(2026, 5, 25));
		assertThat(Holiday.LABOR_DAY.on(2026)).isEqualTo(LocalDate.of(2026, 9, 7));
		assertThat(Holiday.THANKSGIVING.on(2026)).isEqualTo(LocalDate.of(2026, 11, 26));
		assertThat(Holiday.THANKSGIVING.on(2027)).isEqualTo(LocalDate.of(2027, 11, 25));
	}

	@Test
	void theOwnerChecksHolidaysAndTheyAreClosedEveryYear() throws Exception {
		mvc.perform(get("/api/owner/holidays").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(Holiday.values().length))
			.andExpect(jsonPath("$[?(@.holiday=='THANKSGIVING')].on").value(false));
		holiday(Holiday.THANKSGIVING, true);
		holiday(Holiday.CHRISTMAS_DAY, true)
			.andExpect(jsonPath("$[?(@.holiday=='CHRISTMAS_DAY')].on").value(true))
			.andExpect(jsonPath("$[?(@.holiday=='CHRISTMAS_DAY')].label").value("Christmas Day"));
		// Only owners change the list.
		mvc.perform(put("/api/owner/holidays").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"holiday\":\"EASTER\",\"on\":true}"))
			.andExpect(status().isForbidden());

		mvc.perform(get("/api/closed-days").cookie(ava).param("from", "2026-11-01").param("to", "2027-12-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].date").value(org.hamcrest.Matchers.contains(
				"2026-11-26", "2026-12-25", "2027-11-25", "2027-12-25")))
			.andExpect(jsonPath("$[0].closed").value(true))
			.andExpect(jsonPath("$[0].reason").value("Thanksgiving"));
	}

	@Test
	void theCalendarClosesOtherDaysAndOpensAHolidayForOneYear() throws Exception {
		holiday(Holiday.CHRISTMAS_DAY, true);

		// A one-time closure, with a reason.
		mark(gm, "2026-12-05", true, "Snow day")
			.andExpect(jsonPath("$.closed").value(true))
			.andExpect(jsonPath("$.reason").value("Snow day"));
		// Open on Christmas this year only.
		mark(gm, "2026-12-25", false, null)
			.andExpect(jsonPath("$.closed").value(false))
			.andExpect(jsonPath("$.holiday").value("Christmas Day"));
		mvc.perform(get("/api/closed-days").cookie(gm).param("from", "2026-12-01").param("to", "2027-12-31"))
			.andExpect(jsonPath("$[*].date").value(org.hamcrest.Matchers.contains("2026-12-05", "2026-12-25", "2027-12-25")))
			.andExpect(jsonPath("$[1].closed").value(false))
			.andExpect(jsonPath("$[2].closed").value(true));

		// Closing Christmas again just goes back to the holiday: nothing extra is kept.
		mark(owner, "2026-12-25", true, null).andExpect(jsonPath("$.reason").value("Christmas Day"));
		assertThat(closedDays.findById(LocalDate.of(2026, 12, 25))).isEmpty();
		// Reopening the snow day removes it.
		mark(owner, "2026-12-05", false, null).andExpect(jsonPath("$.closed").value(false));
		assertThat(closedDays.count()).isZero();

		// Staff who don't approve time off can look but not change.
		mvc.perform(put("/api/closed-days/2026-12-06").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"closed\":true}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void uncheckingAHolidayForgetsThatItWasOpenedForAYear() throws Exception {
		holiday(Holiday.CHRISTMAS_DAY, true);
		mark(gm, "2026-12-25", false, null);
		holiday(Holiday.CHRISTMAS_DAY, false);
		mvc.perform(get("/api/closed-days").cookie(gm).param("from", "2026-12-01").param("to", "2026-12-31"))
			.andExpect(jsonPath("$.length()").value(0));
	}

	private ResultActions holiday(Holiday h, boolean on) throws Exception {
		return mvc.perform(put("/api/owner/holidays").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"holiday\":\"" + h + "\",\"on\":" + on + "}"))
			.andExpect(status().isOk());
	}

	private ResultActions mark(Cookie who, String date, boolean closed, String note) throws Exception {
		return mvc.perform(put("/api/closed-days/" + date).cookie(who).contentType(MediaType.APPLICATION_JSON)
				.content("{\"closed\":" + closed + ",\"note\":" + (note == null ? "null" : "\"" + note + "\"") + "}"))
			.andExpect(status().isOk());
	}

	private void clear() {
		closedDays.deleteAll();
		settings.findAll().stream().filter(s -> s.getKey().equals("closed.holidays") || s.getKey().startsWith("permission."))
			.forEach(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		Cookie c = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		assertThat(c).isNotNull();
		return c;
	}
}
