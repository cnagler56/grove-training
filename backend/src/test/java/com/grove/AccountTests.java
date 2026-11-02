package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.grove.Domain.Role;
import com.grove.Repository.UserRepository;
import com.grove.Service.SessionService;

/** Staff change their own password, and only by knowing the current one. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountTests {

	private static final AtomicInteger NEXT = new AtomicInteger();
	private static final String OLD = "password1";
	private static final String NEW = "new-password2";

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;

	private String email;
	private Cookie cookie;

	@BeforeEach
	void setUp() throws Exception {
		email = "account" + NEXT.incrementAndGet() + "@grove.test";
		cookie = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + OLD + "\",\"teamCode\":\"test-code\",\"firstName\":\"Ava\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		assertThat(cookie).isNotNull();
	}

	@Test
	void theRightCurrentPasswordChangesIt() throws Exception {
		change(cookie, OLD, NEW).andExpect(status().isNoContent());

		login(NEW).andExpect(status().isOk());
		login(OLD).andExpect(status().isUnauthorized());
	}

	@Test
	void everyStaffRoleCanChangeTheirs() throws Exception {
		String current = OLD;
		for (Role role : Arrays.stream(Role.values()).filter(Role::isStaff).toList()) {
			setRole(role);
			String next = "password-" + role;
			change(cookie, current, next).andExpect(status().isNoContent());
			current = next;
		}
		login(current).andExpect(status().isOk());
	}

	@Test
	void aWrongCurrentPasswordIsRefused() throws Exception {
		change(cookie, "not-my-password", NEW)
			.andExpect(status().isForbidden())
			.andExpect(status().reason("That isn't your current password"));

		login(OLD).andExpect(status().isOk());
		login(NEW).andExpect(status().isUnauthorized());
	}

	@Test
	void aMissingCurrentPasswordIsRefused() throws Exception {
		mvc.perform(post("/api/account/password").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"newPassword\":\"" + NEW + "\"}"))
			.andExpect(status().isForbidden());
		login(OLD).andExpect(status().isOk());
	}

	@Test
	void aShortOrUnchangedNewPasswordIsRefused() throws Exception {
		change(cookie, OLD, "short").andExpect(status().isBadRequest());
		change(cookie, OLD, OLD).andExpect(status().isBadRequest());
		login(OLD).andExpect(status().isOk());
	}

	@Test
	void customersAreRefused() throws Exception {
		setRole(Role.CUSTOMER);
		change(cookie, OLD, NEW).andExpect(status().isForbidden());
		login(OLD).andExpect(status().isOk());
	}

	@Test
	void signedOutIsRefused() throws Exception {
		mvc.perform(post("/api/account/password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEW + "\"}"))
			.andExpect(status().isUnauthorized());
		login(OLD).andExpect(status().isOk());
	}

	@Test
	void changingItSignsOutOtherDevicesButNotThisOne() throws Exception {
		Cookie otherDevice = login(OLD).andExpect(status().isOk())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		assertThat(otherDevice).isNotNull();

		change(cookie, OLD, NEW).andExpect(status().isNoContent());

		mvc.perform(get("/me").cookie(cookie)).andExpect(status().isOk());
		mvc.perform(get("/me").cookie(otherDevice)).andExpect(status().isUnauthorized());
	}

	private ResultActions change(Cookie c, String current, String next) throws Exception {
		return mvc.perform(post("/api/account/password").cookie(c).contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
	}

	private ResultActions login(String password) throws Exception {
		return mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private void setRole(Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
	}
}
