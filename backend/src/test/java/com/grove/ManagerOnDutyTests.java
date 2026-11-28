package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
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

import com.grove.Domain.Role;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.ManagerOnDutyRepository;
import com.grove.Repository.ScheduleNoteRepository;
import com.grove.Repository.ScheduleWeekRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;

/** The schedule's MOD row (manager on duty) and the owner's list of who can be picked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagerOnDutyTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired ShiftRepository shifts;
	@Autowired ScheduleWeekRepository weeks;
	@Autowired ScheduleNoteRepository notes;
	@Autowired ManagerOnDutyRepository mods;
	@Autowired AppSettingRepository settings;
	@MockBean JavaMailSender mailSender;

	/** A Saturday safely in the future. */
	private final LocalDate week = ScheduleService.weekStart(LocalDate.now().plusDays(30));
	private Cookie owner;
	private Cookie gm;
	private Cookie ava;
	private long ownerId;
	private long giaId;
	private long maxId;
	private long louId;
	private long avaId;

	@BeforeEach
	void setUp() throws Exception {
		clear();
		owner = signUp("owner@grove.test", "Owner", "Person");
		gm = signUp("gia@grove.test", "Gia", "Ray");
		signUp("max@grove.test", "Max", "Hart");
		signUp("lou@grove.test", "Lou", "Park");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		ownerId = idOf("owner@grove.test");
		giaId = setRole("gia@grove.test", Role.GENERAL_MANAGER);
		maxId = setRole("max@grove.test", Role.MANAGER);
		louId = setRole("lou@grove.test", Role.LEAD);
		avaId = idOf("ava@grove.test");
	}

	@AfterEach
	void tearDown() {
		clear();
	}

	@Test
	void theModRowSavesWithTheWeekAndStaffSeeItOncePosted() throws Exception {
		// Until the owner changes the list, general managers and managers can be picked.
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(gm))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.managersOnDuty.length()").value(0))
			.andExpect(jsonPath("$.modChoices[*].name").value(contains("Gia Ray", "Max Hart")));

		save(gm, "[{\"userId\":" + maxId + ",\"date\":\"" + week + "\",\"type\":\"WORK\",\"start\":\"09:00\",\"end\":\"17:00\"}]",
				mod(week, maxId) + "," + mod(week.plusDays(1), giaId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.managersOnDuty.length()").value(2))
			.andExpect(jsonPath("$.managersOnDuty[0].date").value(week.toString()))
			.andExpect(jsonPath("$.managersOnDuty[0].name").value("Max Hart"))
			.andExpect(jsonPath("$.managersOnDuty[1].name").value("Gia Ray"))
			.andExpect(jsonPath("$.shifts.length()").value(1));

		// Staff don't see a draft's MOD row, or the list of who can be picked.
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(ava))
			.andExpect(jsonPath("$.managersOnDuty.length()").value(0))
			.andExpect(jsonPath("$.modChoices.length()").value(0));
		mvc.perform(put("/api/schedule/" + week + "/published").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"published\":true}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(ava))
			.andExpect(jsonPath("$.managersOnDuty[*].name").value(contains("Max Hart", "Gia Ray")))
			.andExpect(jsonPath("$.modChoices.length()").value(0));

		// Saving without a MOD row leaves it alone; sending one replaces it.
		mvc.perform(put("/api/schedule/" + week).cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shifts\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.managersOnDuty.length()").value(2));
		save(gm, "[]", mod(week.plusDays(1), maxId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.managersOnDuty.length()").value(1))
			.andExpect(jsonPath("$.managersOnDuty[0].date").value(week.plusDays(1).toString()))
			.andExpect(jsonPath("$.managersOnDuty[0].name").value("Max Hart"));
		assertThat(mods.count()).isEqualTo(1);
	}

	@Test
	void onlyPeopleTheOwnerChecksCanBePicked() throws Exception {
		// A lead and a stylist aren't on the list to start.
		save(gm, "[]", mod(week, louId))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason(containsString("Lou Park can't be manager on duty")));
		save(gm, "[]", mod(week, avaId)).andExpect(status().isBadRequest());
		// Nor is the owner until she checks herself.
		save(gm, "[]", mod(week, ownerId)).andExpect(status().isBadRequest());
		// One per day.
		save(gm, "[]", mod(week, maxId) + "," + mod(week, giaId))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason(containsString("only be one manager on duty")));
		// Days outside the week are refused.
		save(gm, "[]", mod(week.plusDays(7), maxId)).andExpect(status().isBadRequest());
		assertThat(mods.count()).isZero();

		// The owner's Settings card lists leads and up, owners included, but no stylists.
		mvc.perform(get("/api/owner/manager-on-duty").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(contains("Gia Ray", "Lou Park", "Max Hart", "Owner Person")))
			.andExpect(jsonPath("$[?(@.name=='Max Hart')].on").value(true))
			.andExpect(jsonPath("$[?(@.name=='Lou Park')].on").value(false))
			.andExpect(jsonPath("$[?(@.name=='Owner Person')].on").value(false));
		// Only the owner changes it.
		mvc.perform(put("/api/owner/manager-on-duty").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + louId + ",\"on\":true}"))
			.andExpect(status().isForbidden());
		// A stylist can't be added.
		mvc.perform(put("/api/owner/manager-on-duty").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + avaId + ",\"on\":true}"))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason(containsString("Only leads, managers, general managers, and owners")));

		// The owner adds the lead and herself: now both can be picked.
		modList(louId, true).andExpect(jsonPath("$[?(@.name=='Lou Park')].on").value(true));
		modList(ownerId, true).andExpect(jsonPath("$[?(@.name=='Owner Person')].on").value(true));
		save(gm, "[]", mod(week, louId) + "," + mod(week.plusDays(1), maxId) + "," + mod(week.plusDays(2), ownerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.managersOnDuty[*].name").value(contains("Lou Park", "Max Hart", "Owner Person")));

		// The owner takes the manager off: the day already saved stays, but he can't be picked again.
		modList(maxId, false)
			.andExpect(jsonPath("$[?(@.name=='Max Hart')].on").value(false))
			.andExpect(jsonPath("$[?(@.name=='Gia Ray')].on").value(true));
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(gm))
			.andExpect(jsonPath("$.modChoices[*].name").value(contains("Gia Ray", "Lou Park", "Owner Person")));
		save(gm, "[]", mod(week, louId) + "," + mod(week.plusDays(1), maxId)).andExpect(status().isOk());
		save(gm, "[]", mod(week.plusDays(2), maxId))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason(containsString("Max Hart can't be manager on duty")));
		assertThat(mods.findById(week.plusDays(1))).hasValueSatisfying(m -> assertThat(m.getUserId()).isEqualTo(maxId));
	}

	private static String mod(LocalDate date, long userId) {
		return "{\"date\":\"" + date + "\",\"userId\":" + userId + "}";
	}

	private ResultActions save(Cookie who, String shiftsJson, String modsJson) throws Exception {
		return mvc.perform(put("/api/schedule/" + week).cookie(who).contentType(MediaType.APPLICATION_JSON)
			.content("{\"shifts\":" + shiftsJson + ",\"managersOnDuty\":[" + modsJson + "]}"));
	}

	private ResultActions modList(long userId, boolean on) throws Exception {
		return mvc.perform(put("/api/owner/manager-on-duty").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + userId + ",\"on\":" + on + "}"))
			.andExpect(status().isOk());
	}

	private long idOf(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private long setRole(String email, Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		return users.save(u).getUserId();
	}

	private void clear() {
		mods.deleteAll();
		shifts.deleteAll();
		notes.deleteAll();
		weeks.deleteAll();
		settings.findAll().stream()
			.filter(s -> s.getKey().equals("schedule.modEligible") || s.getKey().startsWith("permission."))
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
