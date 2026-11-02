package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

import com.grove.Repository.UserNotificationRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.NotificationService;
import com.grove.Service.PushService;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired UserNotificationRepository notifications;
	@Autowired PushService push;
	@Autowired NotificationService service;
	@MockBean JavaMailSender mailSender;

	private Cookie owner, ava;
	private Long ownerId, avaId, miaId;

	@BeforeEach
	void setUp() throws Exception {
		notifications.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		signUp("mia@grove.test", "Mia", "Cruz");
		ownerId = id("owner@grove.test");
		avaId = id("ava@grove.test");
		miaId = id("mia@grove.test");
	}

	@Test
	void everythingSentIsListedNewestFirstEvenWithoutPhoneNotifications() throws Exception {
		push.notify(List.of(avaId), "Time off approved", "Owner approved Dec 7", "/time-off");
		push.notify(List.of(avaId, miaId), "Treatment Blitz", "You moved up to 1st", "/contests");

		mvc.perform(get("/api/notifications").cookie(ava))
			.andExpect(jsonPath("$.unread").value(2))
			.andExpect(jsonPath("$.notifications.length()").value(2))
			.andExpect(jsonPath("$.notifications[0].title").value("Treatment Blitz"))
			.andExpect(jsonPath("$.notifications[0].body").value("You moved up to 1st"))
			.andExpect(jsonPath("$.notifications[0].url").value("/contests"))
			.andExpect(jsonPath("$.notifications[0].read").value(false))
			.andExpect(jsonPath("$.notifications[1].title").value("Time off approved"));
		// Only your own.
		mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.notifications.length()").value(0));
		mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
	}

	@Test
	void aRealFeatureLandsInTheList() throws Exception {
		mvc.perform(post("/api/team-board").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Holiday hours are up\",\"withPhotos\":false}"))
			.andExpect(status().is2xxSuccessful());
		mvc.perform(get("/api/notifications").cookie(ava))
			.andExpect(jsonPath("$.notifications[0].title").value("Owner Person posted on the team board"))
			.andExpect(jsonPath("$.notifications[0].url").value("/home"));
		mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void disabledAccountsGetNothing() throws Exception {
		var mia = users.findById(miaId).orElseThrow();
		mia.setActive(false);
		users.save(mia);
		push.notify(List.of(avaId, miaId), "Hi", null, "/home");
		assertThat(notifications.findAll()).hasSize(1);
	}

	@Test
	void openingOneOrItsPageOrMarkingAllReadClearsTheCount() throws Exception {
		push.notify(List.of(avaId), "Time off approved", null, "/time-off");
		push.notify(List.of(avaId), "Time off sent back", null, "/time-off");
		push.notify(List.of(avaId), "Treatment Blitz", null, "/contests");
		push.notify(List.of(avaId), "New video", null, "/videos");

		// Opening a page marks what points to it read.
		mvc.perform(post("/api/notifications/seen").cookie(ava).contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/time-off\"}"))
			.andExpect(jsonPath("$.unread").value(2));
		mvc.perform(post("/api/notifications/seen").cookie(ava).contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/schedule\"}"))
			.andExpect(jsonPath("$.unread").value(2));
		// Home is where the app opens, so landing there doesn't count as seeing team board posts.
		push.notify(List.of(avaId), "Owner posted on the team board", null, "/home");
		mvc.perform(post("/api/notifications/seen").cookie(ava).contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/home\"}"))
			.andExpect(jsonPath("$.unread").value(3));
		long home = notifications.findAll().stream().filter(n -> n.getUrl().equals("/home")).findFirst().orElseThrow().getId();
		mvc.perform(put("/api/notifications/" + home + "/read").cookie(ava)).andExpect(status().isNoContent());

		long contests = notifications.findAll().stream().filter(n -> n.getUrl().equals("/contests")).findFirst().orElseThrow().getId();
		mvc.perform(put("/api/notifications/" + contests + "/read").cookie(owner)).andExpect(status().isNotFound());
		mvc.perform(put("/api/notifications/" + contests + "/read").cookie(ava)).andExpect(status().isNoContent());
		mvc.perform(get("/api/notifications").cookie(ava))
			.andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.notifications[1].title").value("New video"))
			.andExpect(jsonPath("$.notifications[1].read").value(false))
			.andExpect(jsonPath("$.notifications[2].title").value("Treatment Blitz"))
			.andExpect(jsonPath("$.notifications[2].read").value(true));

		mvc.perform(put("/api/notifications/read-all").cookie(ava)).andExpect(status().isNoContent());
		mvc.perform(get("/api/notifications").cookie(ava)).andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void theListGoesBackThirtyDaysAndOldOnesAreCleanedUp() throws Exception {
		push.notify(List.of(avaId), "Recent", null, "/home");
		push.notify(List.of(avaId), "Last month", null, "/home");
		push.notify(List.of(avaId), "Long ago", null, "/home");
		LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Chicago"));
		notifications.findAll().forEach(n -> {
			if (n.getTitle().equals("Last month")) n.setCreatedAt(now.minusDays(40));
			if (n.getTitle().equals("Long ago")) n.setCreatedAt(now.minusDays(70));
			notifications.save(n);
		});
		mvc.perform(get("/api/notifications").cookie(ava))
			.andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.notifications.length()").value(1));
		service.cleanup();
		assertThat(notifications.findAll()).extracting(n -> n.getTitle()).containsExactlyInAnyOrder("Recent", "Last month");
	}

	@Test
	void deletingAnAccountDeletesTheirNotifications() throws Exception {
		push.notify(List.of(avaId, ownerId), "Hi", null, "/home");
		mvc.perform(delete("/api/admin/team/" + avaId).cookie(owner)).andExpect(status().is2xxSuccessful());
		assertThat(notifications.findAll()).hasSize(1);
	}

	private Long id(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
