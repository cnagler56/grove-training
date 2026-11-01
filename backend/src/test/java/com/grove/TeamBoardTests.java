package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.TeamPostCommentRepository;
import com.grove.Repository.TeamPostImageRepository;
import com.grove.Repository.TeamPostLikeRepository;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamBoardTests {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired TeamPostRepository posts;
	@Autowired TeamPostImageRepository images;
	@Autowired TeamPostCommentRepository comments;
	@Autowired TeamPostLikeRepository likes;
	@Autowired AppSettingRepository settings;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private Cookie manager;
	private Cookie stylist;
	private Cookie customer;

	@BeforeEach
	void setUp() throws Exception {
		images.deleteAll();
		comments.deleteAll();
		likes.deleteAll();
		posts.deleteAll();
		settings.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		manager = signUp("max@grove.test", "Max", "Lee");
		stylist = signUp("ava@grove.test", "Ava", "Stone");
		customer = signUp("cal@grove.test", "Cal", "Fox");
		setRole("max@grove.test", Role.MANAGER);
		setRole("cal@grove.test", Role.CUSTOMER);
	}

	@Test
	void managersPostAndEveryoneOnStaffReadsCommentsAndLikes() throws Exception {
		mvc.perform(get("/api/me/permissions").cookie(stylist)).andExpect(jsonPath("$.postUpdates").value(false));
		mvc.perform(get("/api/me/permissions").cookie(manager)).andExpect(jsonPath("$.postUpdates").value(true));
		send(stylist, "{\"body\":\"Hi\"}", status().isForbidden());
		send(manager, "{\"body\":\"   \"}", status().isBadRequest());
		long id = create(manager, "Staff meeting Monday at 9");

		mvc.perform(get("/api/team-board").cookie(stylist))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].body").value("Staff meeting Monday at 9"))
			.andExpect(jsonPath("$.posts[0].authorName").value("Max Lee"))
			.andExpect(jsonPath("$.posts[0].canDelete").value(false));

		comment(stylist, id, "I'll be there").andExpect(status().isCreated());
		comment(stylist, id, "  ").andExpect(status().isBadRequest());
		mvc.perform(put("/api/team-board/" + id + "/like").cookie(stylist)).andExpect(status().isNoContent());
		mvc.perform(put("/api/team-board/" + id + "/like").cookie(stylist)).andExpect(status().isNoContent());

		mvc.perform(get("/api/team-board").cookie(stylist))
			.andExpect(jsonPath("$.posts[0].likes").value(1))
			.andExpect(jsonPath("$.posts[0].likedByMe").value(true))
			.andExpect(jsonPath("$.posts[0].comments[0].body").value("I'll be there"))
			.andExpect(jsonPath("$.posts[0].comments[0].authorName").value("Ava Stone"))
			.andExpect(jsonPath("$.posts[0].comments[0].canDelete").value(true));
		mvc.perform(get("/api/team-board").cookie(manager))
			.andExpect(jsonPath("$.posts[0].likedByMe").value(false));

		mvc.perform(delete("/api/team-board/" + id + "/like").cookie(stylist)).andExpect(status().isNoContent());
		mvc.perform(get("/api/team-board").cookie(stylist)).andExpect(jsonPath("$.posts[0].likes").value(0));
	}

	@Test
	void customersAndSignedOutPeopleCantSeeIt() throws Exception {
		long id = create(manager, "Staff only");
		mvc.perform(get("/api/team-board")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/team-board").cookie(customer)).andExpect(status().isForbidden());
		comment(customer, id, "Hello").andExpect(status().isForbidden());
		mvc.perform(put("/api/team-board/" + id + "/like").cookie(customer)).andExpect(status().isForbidden());
		send(customer, "{\"body\":\"Hi\"}", status().isForbidden());
	}

	@Test
	void theOwnerDecidesWhoCanPost() throws Exception {
		mvc.perform(put("/api/owner/permissions").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":{\"POST_UPDATES\":[\"STYLIST\"]}}"))
			.andExpect(status().isOk());
		send(stylist, "{\"body\":\"Now I can post\"}", status().isCreated());
		send(manager, "{\"body\":\"Now I can't\"}", status().isForbidden());
	}

	@Test
	void photosArePrivateAndChecked() throws Exception {
		String body = send(manager, "{\"withPhotos\":true}", status().isCreated());
		long id = Long.parseLong(body.replaceAll("\\D", ""));

		mvc.perform(post("/api/team-board/" + id + "/images").cookie(manager).contentType(MediaType.IMAGE_JPEG).content(new byte[] {1, 2, 3}))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/team-board/" + id + "/images").cookie(stylist).contentType(MediaType.IMAGE_JPEG).content(JPEG))
			.andExpect(status().isForbidden());
		for (int i = 0; i < 6; i++) {
			mvc.perform(post("/api/team-board/" + id + "/images").cookie(manager).contentType(MediaType.IMAGE_JPEG).content(JPEG))
				.andExpect(status().isCreated());
		}
		mvc.perform(post("/api/team-board/" + id + "/images").cookie(manager).contentType(MediaType.IMAGE_JPEG).content(JPEG))
			.andExpect(status().isBadRequest());

		String feed = mvc.perform(get("/api/team-board").cookie(stylist))
			.andExpect(jsonPath("$.posts[0].imageIds.length()").value(6))
			.andReturn().getResponse().getContentAsString();
		long imageId = Long.parseLong(feed.replaceAll(".*\"imageIds\":\\[(\\d+).*", "$1"));

		byte[] served = mvc.perform(get("/api/team-board/images/" + imageId).cookie(stylist))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(served).isEqualTo(JPEG);
		mvc.perform(get("/api/team-board/images/" + imageId)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/team-board/images/" + imageId).cookie(customer)).andExpect(status().isForbidden());
	}

	@Test
	void deletingAndPinning() throws Exception {
		long first = create(manager, "First");
		long second = create(manager, "Second");

		// Newest first until something is pinned; only managers and up can pin.
		mvc.perform(get("/api/team-board").cookie(stylist)).andExpect(jsonPath("$.posts[0].body").value("Second"));
		pin(stylist, first).andExpect(status().isForbidden());
		pin(manager, first).andExpect(status().isNoContent());
		mvc.perform(get("/api/team-board").cookie(stylist))
			.andExpect(jsonPath("$.posts[0].body").value("First"))
			.andExpect(jsonPath("$.posts[0].pinned").value(true));

		// Your own comment, or any as a manager; not someone else's.
		comment(stylist, first, "Mine").andExpect(status().isCreated());
		Long commentId = comments.findAll().get(0).getId();
		signUp("ben@grove.test", "Ben", "Cole");
		Cookie ben = login("ben@grove.test");
		mvc.perform(delete("/api/team-board/comments/" + commentId).cookie(ben)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/team-board/comments/" + commentId).cookie(stylist)).andExpect(status().isNoContent());

		// A post takes its comments, likes, and photos with it.
		comment(stylist, second, "Bye").andExpect(status().isCreated());
		mvc.perform(put("/api/team-board/" + second + "/like").cookie(stylist)).andExpect(status().isNoContent());
		mvc.perform(post("/api/team-board/" + second + "/images").cookie(manager).contentType(MediaType.IMAGE_JPEG).content(JPEG))
			.andExpect(status().isCreated());
		mvc.perform(delete("/api/team-board/" + second).cookie(stylist)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/team-board/" + second).cookie(owner)).andExpect(status().isNoContent());
		assertThat(comments.findAll()).isEmpty();
		assertThat(likes.findAll()).isEmpty();
		assertThat(images.findAll()).isEmpty();
	}

	@Test
	void theFeedComesInPages() throws Exception {
		for (int i = 1; i <= 12; i++) create(manager, "Post " + i);
		mvc.perform(get("/api/team-board").cookie(stylist))
			.andExpect(jsonPath("$.posts.length()").value(10))
			.andExpect(jsonPath("$.hasMore").value(true));
		mvc.perform(get("/api/team-board").param("page", "1").cookie(stylist))
			.andExpect(jsonPath("$.posts.length()").value(2))
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	private long create(Cookie who, String text) throws Exception {
		String body = send(who, "{\"body\":\"" + text + "\"}", status().isCreated());
		return Long.parseLong(body.replaceAll("\\D", ""));
	}

	private String send(Cookie who, String json, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
		return mvc.perform(post("/api/team-board").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(expected)
			.andReturn().getResponse().getContentAsString();
	}

	private org.springframework.test.web.servlet.ResultActions comment(Cookie who, long postId, String text) throws Exception {
		return mvc.perform(post("/api/team-board/" + postId + "/comments").cookie(who).contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"" + text + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions pin(Cookie who, long postId) throws Exception {
		return mvc.perform(put("/api/team-board/" + postId + "/pinned").cookie(who).contentType(MediaType.APPLICATION_JSON)
			.content("{\"pinned\":true}"));
	}

	private void setRole(String email, Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
	}

	private Cookie login(String email) throws Exception {
		return mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
