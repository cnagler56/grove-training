package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Domain.Video;
import com.grove.Domain.VideoStatus;
import com.grove.Repository.JobApplicationRepository;
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
	@Autowired JobApplicationRepository applications;
	@MockBean JavaMailSender mailSender;

	@BeforeEach
	void clean() {
		applications.deleteAll();
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
			.andExpect(jsonPath("$.roles").value("STYLIST"))
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void adminEmailBecomesManager() throws Exception {
		mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"Owner@Grove.test\",\"password\":\"password1\",\"teamCode\":\"test-code\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.roles").value("OWNER"));
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
	void managersOnlyChangePeopleBelowThem() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		Cookie manager = signUp("manager@grove.test");
		signUp("gm@grove.test");
		signUp("stylist@grove.test");
		setRole("manager@grove.test", Role.MANAGER);
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		Long gm = idOf("gm@grove.test");
		Long stylist = idOf("stylist@grove.test");

		// A manager can make a stylist a lead, but not a manager (their own level) or higher.
		mvc.perform(roleChange(manager, stylist, "LEAD")).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("LEAD"));
		mvc.perform(roleChange(manager, stylist, "MANAGER")).andExpect(status().isForbidden());

		// ...and can't touch a general manager at all.
		mvc.perform(roleChange(manager, gm, "STYLIST")).andExpect(status().isForbidden());
		mvc.perform(put("/api/admin/team/" + gm).cookie(manager).contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isForbidden());

		// The owner can give any role, including owner.
		mvc.perform(roleChange(owner, stylist, "OWNER")).andExpect(status().isOk());
	}

	@Test
	void theLastOwnerCantBeRemoved() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		signUp("second@grove.test");
		setRole("second@grove.test", Role.OWNER);
		Cookie second = login("second@grove.test");
		Long first = idOf("owner@grove.test");
		Long secondId = idOf("second@grove.test");

		// With two owners, one can step the other down; then the last one is protected.
		mvc.perform(roleChange(second, first, "GENERAL_MANAGER")).andExpect(status().isOk());
		mvc.perform(roleChange(second, secondId, "MANAGER")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/admin/team").cookie(owner)).andExpect(status().isOk()); // a GM still manages
	}

	@Test
	void customersCantSeeTrainingVideos() throws Exception {
		Cookie customer = signUp("client@grove.test");
		setRole("client@grove.test", Role.CUSTOMER);
		Long id = seedVideo("Color theory", VideoStatus.READY);

		mvc.perform(get("/api/videos").cookie(customer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/videos/" + id).cookie(customer)).andExpect(status().isNotFound());
		mvc.perform(get("/api/admin/team").cookie(customer)).andExpect(status().isForbidden());
	}

	private static final String APPLICATION = "{\"firstName\":\"Pat\",\"lastName\":\"Rivera\",\"email\":\"Pat@Example.com\","
		+ "\"phone\":\"555-0199\",\"position\":\"Stylist\",\"licensed\":true,\"yearsExperience\":4,"
		+ "\"availability\":\"Weekends\",\"message\":\"Love color work\"}";

	@Test
	void anyoneCanApplyButOnlyOwnersSeeApplications() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		signUp("gm@grove.test");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		Cookie gm = login("gm@grove.test");

		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.1")
				.contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/owner/applications")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/owner/applications").cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(get("/api/owner/applications").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].email").value("pat@example.com"))
			.andExpect(jsonPath("$[0].status").value("NEW"));

		Long id = applications.findAll().get(0).getId();
		mvc.perform(put("/api/owner/applications/" + id).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"INTERVIEW\",\"ownerNotes\":\"Call Tuesday\"}"))
			.andExpect(jsonPath("$.status").value("INTERVIEW"))
			.andExpect(jsonPath("$.ownerNotes").value("Call Tuesday"));
	}

	@Test
	void applicationFormValidatesAndDropsBots() throws Exception {
		// Missing phone.
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.2").contentType(MediaType.APPLICATION_JSON)
				.content(APPLICATION.replace("\"phone\":\"555-0199\",", "")))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason("Phone is required"));
		// Made-up position.
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.2").contentType(MediaType.APPLICATION_JSON)
				.content(APPLICATION.replace("Stylist", "CEO")))
			.andExpect(status().isBadRequest());
		// Honeypot filled: looks accepted, but nothing is saved.
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.2").contentType(MediaType.APPLICATION_JSON)
				.content(APPLICATION.replace("}", ",\"website\":\"http://spam.example\"}")))
			.andExpect(status().isCreated());
		assertThat(applications.count()).isZero();
	}

	@Test
	void ownerAddsAndDeletesEmployees() throws Exception {
		Cookie owner = signUp("owner@grove.test");
		signUp("gm@grove.test");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		Cookie gm = login("gm@grove.test");
		String newHire = "{\"firstName\":\"Robin\",\"lastName\":\"Hale\",\"email\":\"Robin@Grove.test\","
			+ "\"phone\":\"555-0123\",\"hireDate\":\"2026-10-01\",\"role\":\"RECEPTIONIST\",\"password\":\"password1\"}";

		// Only owners can add or delete.
		mvc.perform(post("/api/admin/team").cookie(gm).contentType(MediaType.APPLICATION_JSON).content(newHire))
			.andExpect(status().isForbidden());

		mvc.perform(post("/api/admin/team").cookie(owner).contentType(MediaType.APPLICATION_JSON).content(newHire))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("robin@grove.test"))
			.andExpect(jsonPath("$.role").value("RECEPTIONIST"))
			.andExpect(jsonPath("$.hireDate").value("2026-10-01"));
		mvc.perform(post("/api/admin/team").cookie(owner).contentType(MediaType.APPLICATION_JSON).content(newHire))
			.andExpect(status().isConflict());

		// The new hire signs in with the temporary password.
		Cookie robin = login("robin@grove.test");
		Long robinId = idOf("robin@grove.test");
		seedVideo("Front desk basics", VideoStatus.READY);
		mvc.perform(put("/api/videos/" + videos.findAll().get(0).getId() + "/completed").cookie(robin))
			.andExpect(status().isNoContent());

		mvc.perform(delete("/api/admin/team/" + robinId).cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/admin/team/" + robinId).cookie(owner)).andExpect(status().isNoContent());
		assertThat(users.findByEmail("robin@grove.test")).isEmpty();
		assertThat(completions.count()).isZero();
		mvc.perform(get("/me").cookie(robin)).andExpect(status().isUnauthorized());

		// Can't delete yourself (and so never the last owner).
		mvc.perform(delete("/api/admin/team/" + idOf("owner@grove.test")).cookie(owner))
			.andExpect(status().isBadRequest());
	}

	@Test
	void onlyOwnersAreEmailedAboutApplications() throws Exception {
		signUp("owner@grove.test");
		signUp("gm@grove.test");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		signUp("stylist@grove.test");

		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.5")
				.contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
			.andExpect(status().isCreated());

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, timeout(3000)).send(sent.capture());
		assertThat(sent.getValue().getTo()).containsExactly("owner@grove.test");
		assertThat(sent.getValue().getSubject()).contains("Pat Rivera").contains("Stylist");

		// Bots caught by the honeypot don't trigger an email.
		org.mockito.Mockito.clearInvocations(mailSender);
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.6").contentType(MediaType.APPLICATION_JSON)
				.content(APPLICATION.replace("}", ",\"website\":\"x\"}")))
			.andExpect(status().isCreated());
		Thread.sleep(300);
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void applicationsAreRateLimited() throws Exception {
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.3")
					.contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
				.andExpect(status().isCreated());
		}
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.3")
				.contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
			.andExpect(status().isTooManyRequests());
		// A different connection is unaffected.
		mvc.perform(post("/api/apply").header("X-Forwarded-For", "10.0.0.4")
				.contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
			.andExpect(status().isCreated());
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

	private Cookie login(String email) throws Exception {
		String setCookie = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getHeader("Set-Cookie");
		return new Cookie(SessionService.COOKIE_NAME, setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';')));
	}

	private void setRole(String email, Role role) {
		User u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
	}

	private Long idOf(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private MockHttpServletRequestBuilder roleChange(Cookie actor, Long userId, String role) {
		return put("/api/admin/team/" + userId).cookie(actor).contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"" + role + "\"}");
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
