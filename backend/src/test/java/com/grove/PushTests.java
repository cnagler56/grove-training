package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

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
import com.grove.Repository.ScheduleWeekRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.TeamPostCommentRepository;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.TimeOffRequestRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.PushService;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;

/** The right people get told about each event, and nobody is told about their own action. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired TimeOffRequestRepository timeOff;
	@Autowired TeamPostRepository posts;
	@Autowired TeamPostCommentRepository comments;
	@Autowired ScheduleWeekRepository weeks;
	@Autowired ShiftRepository shifts;
	@Autowired AppSettingRepository settings;
	@MockBean PushService push;
	@MockBean JavaMailSender mailSender;

	private final LocalDate week = ScheduleService.weekStart(LocalDate.now().plusDays(30));
	private Cookie owner;
	private Cookie gm;
	private Cookie ava;
	private Long ownerId;
	private Long gmId;
	private Long avaId;

	@BeforeEach
	void setUp() throws Exception {
		comments.deleteAll();
		posts.deleteAll();
		timeOff.deleteAll();
		shifts.deleteAll();
		weeks.deleteAll();
		settings.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		gm = signUp("gia@grove.test", "Gia", "Ray");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		setRole("gia@grove.test", Role.GENERAL_MANAGER);
		ownerId = id("owner@grove.test");
		gmId = id("gia@grove.test");
		avaId = id("ava@grove.test");
		reset(push);
	}

	@Test
	void timeOffGoesToApproversThenBackToTheEmployee() throws Exception {
		String body = mvc.perform(post("/api/time-off").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + week + "\",\"endDate\":\"" + week + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		verify(push).notify(to(ownerId, gmId), eq("Time off request"), anyString(), eq("/time-off"));

		long id = Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(gm)).andExpect(status().isOk());
		verify(push).notify(to(avaId), org.mockito.ArgumentMatchers.startsWith("Time off approved"), anyString(), eq("/time-off"));
	}

	@Test
	void postingAWeekTellsStaffOnlyTheFirstTime() throws Exception {
		mvc.perform(put("/api/schedule/" + week + "/published").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"published\":true}"))
			.andExpect(status().isOk());
		verify(push).notify(to(gmId, avaId), eq("New schedule posted"), anyString(), eq("/schedule"));

		reset(push);
		mvc.perform(put("/api/schedule/" + week + "/published").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"published\":true}"))
			.andExpect(status().isOk());
		verify(push, never()).notify(any(), eq("New schedule posted"), anyString(), anyString());
	}

	@Test
	void sendingAWeekTellsTheApproverAndSendingBackTellsTheBuilder() throws Exception {
		mvc.perform(post("/api/schedule/" + week + "/submit").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"approverId\":" + ownerId + "}"))
			.andExpect(status().isOk());
		verify(push).notify(to(ownerId), eq("Schedule to approve"), anyString(), anyString());

		mvc.perform(post("/api/schedule/" + week + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"Fix Tuesday\"}"))
			.andExpect(status().isOk());
		verify(push).notify(to(gmId), eq("Schedule sent back"), org.mockito.ArgumentMatchers.contains("Fix Tuesday"), anyString());
	}

	@Test
	void boardPostsTellStaffAndCommentsTellTheAuthor() throws Exception {
		when(push.staffExcept(gmId)).thenReturn(Set.of(ownerId, avaId));
		String created = mvc.perform(post("/api/team-board").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Staff meeting Monday\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		verify(push).notify(to(ownerId, avaId), eq("Gia Ray posted on the team board"), eq("Staff meeting Monday"), eq("/home"));

		long postId = Long.parseLong(created.replaceAll("\\D", ""));
		mvc.perform(post("/api/team-board/" + postId + "/comments").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Got it\"}"))
			.andExpect(status().isCreated());
		verify(push).notify(to(gmId), eq("Ava Stone commented on your post"), eq("Got it"), eq("/home"));

		// Commenting on your own post doesn't notify you.
		reset(push);
		mvc.perform(post("/api/team-board/" + postId + "/comments").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Thanks\"}"))
			.andExpect(status().isCreated());
		verify(push, never()).notify(any(), anyString(), anyString(), anyString());
	}

	private static Collection<Long> to(Long... ids) {
		return argThat(c -> c != null && Set.copyOf(c).equals(Set.of(ids)));
	}

	private Long id(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private void setRole(String email, Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
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
