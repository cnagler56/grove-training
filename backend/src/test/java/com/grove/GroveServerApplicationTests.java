package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.grove.Domain.Video;
import com.grove.Domain.VideoStatus;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.VideoCompletionRepository;
import com.grove.Repository.VideoRepository;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroveServerApplicationTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired VideoRepository videos;
	@Autowired VideoCompletionRepository completions;

	@BeforeEach
	void clean() {
		completions.deleteAll();
		videos.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
	}

	@Test
	void signupNeedsTheTeamCode() throws Exception {
		mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@grove.test\",\"password\":\"password1\",\"teamCode\":\"wrong\"}"))
			.andExpect(status().isForbidden());

		mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@grove.test\",\"password\":\"password1\",\"teamCode\":\"test-code\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void adminEmailBecomesManager() throws Exception {
		mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"Owner@Grove.test\",\"password\":\"password1\",\"teamCode\":\"test-code\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles").value("ADMIN"));
	}

	@Test
	void employeesCantReachAdminAndOnlySeeReadyVideos() throws Exception {
		Cookie employee = signUp("stylist@grove.test");
		seedVideo("Balayage basics", VideoStatus.READY);
		seedVideo("Still processing", VideoStatus.PROCESSING);

		mvc.perform(get("/api/admin/team").cookie(employee)).andExpect(status().isForbidden());
		mvc.perform(get("/api/videos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/videos").cookie(employee))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Balayage basics"));
	}

	@Test
	void markingWatchedShowsUpForTheManager() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		Cookie employee = signUp("stylist@grove.test");
		Long id = seedVideo("Consultations", VideoStatus.READY);

		mvc.perform(put("/api/videos/" + id + "/completed").cookie(employee)).andExpect(status().isNoContent());
		mvc.perform(get("/api/videos/" + id).cookie(employee))
			.andExpect(jsonPath("$.video.completed").value(true))
			.andExpect(jsonPath("$.embedUrl").isNotEmpty());

		mvc.perform(get("/api/admin/videos/" + id + "/completions").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.employees.length()").value(2))
			.andExpect(jsonPath("$.employees[?(@.email=='stylist@grove.test')].completedAt").isNotEmpty());

		mvc.perform(delete("/api/videos/" + id + "/completed").cookie(employee)).andExpect(status().isNoContent());
		assertThat(completions.count()).isZero();
	}

	@Test
	void deactivatedEmployeeIsSignedOut() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		Cookie employee = signUp("stylist@grove.test");
		Long stylistId = users.findByEmail("stylist@grove.test").orElseThrow().getUserId();

		mvc.perform(put("/api/admin/team/" + stylistId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/me").cookie(employee)).andExpect(status().isUnauthorized());
		mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"stylist@grove.test\",\"password\":\"password1\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void managerEditsEmployeeDetails() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		Cookie employee = signUp("stylist@grove.test");
		signUp("other@grove.test");
		Long id = users.findByEmail("stylist@grove.test").orElseThrow().getUserId();
		String body = "{\"firstName\":\"Sam\",\"lastName\":\"Lee\",\"email\":\"Sam.Lee@Grove.test\","
			+ "\"phone\":\"555-0100\",\"hireDate\":\"2025-03-01\",\"birthday\":\"1995-07-14\"}";

		mvc.perform(put("/api/admin/team/" + id + "/details").cookie(employee)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());

		mvc.perform(put("/api/admin/team/" + id + "/details").cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("sam.lee@grove.test"))
			.andExpect(jsonPath("$.phone").value("555-0100"))
			.andExpect(jsonPath("$.hireDate").value("2025-03-01"))
			.andExpect(jsonPath("$.birthday").value("1995-07-14"));

		// New email is now their sign-in.
		mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"sam.lee@grove.test\",\"password\":\"password1\"}"))
			.andExpect(status().isOk());

		// Can't take another employee's email, or set a future birthday.
		mvc.perform(put("/api/admin/team/" + id + "/details").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"other@grove.test\"}"))
			.andExpect(status().isConflict());
		mvc.perform(put("/api/admin/team/" + id + "/details").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"sam.lee@grove.test\",\"birthday\":\"2999-01-01\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void disablingRecordsTheDateAndReEnablingClearsIt() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		signUp("stylist@grove.test");
		Long id = users.findByEmail("stylist@grove.test").orElseThrow().getUserId();

		mvc.perform(put("/api/admin/team/" + id).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(jsonPath("$.disabledAt").isNotEmpty());
		mvc.perform(put("/api/admin/team/" + id).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":true}"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.disabledAt").isEmpty());
	}

	@Test
	void uploadsReportNotConfiguredWithoutBunnyKeys() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		mvc.perform(get("/api/admin/status").cookie(owner))
			.andExpect(jsonPath("$.bunnyConfigured").value(false));
		mvc.perform(post("/api/admin/videos").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"New cut\"}"))
			.andExpect(status().isServiceUnavailable());
	}

	private Cookie signUp(String email) throws Exception {
		String setCookie = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getHeader("Set-Cookie");
		String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
		return new Cookie(SessionService.COOKIE_NAME, token);
	}

	private Long seedVideo(String title, VideoStatus status) {
		Video v = new Video();
		v.setTitle(title);
		v.setStatus(status);
		v.setBunnyVideoId("guid-" + title.hashCode());
		v.setCreatedAt(LocalDateTime.now());
		return videos.save(v).getId();
	}
}
