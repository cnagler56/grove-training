package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.test.web.servlet.ResultMatcher;

import com.grove.Domain.Role;
import com.grove.Repository.TodoAssignmentRepository;
import com.grove.Repository.TodoItemRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired TodoAssignmentRepository assignments;
	@Autowired TodoItemRepository items;
	@MockBean JavaMailSender mailSender;

	private Cookie owner, gm, manager, manager2, rae, rio, lead, stylist;

	@BeforeEach
	void setUp() throws Exception {
		assignments.deleteAll();
		items.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person", null);
		gm = signUp("gm@grove.test", "Gia", "Ray", Role.GENERAL_MANAGER);
		manager = signUp("manny@grove.test", "Manny", "Reed", Role.MANAGER);
		manager2 = signUp("mo@grove.test", "Mo", "Lane", Role.MANAGER);
		rae = signUp("rae@grove.test", "Rae", "Hill", Role.RECEPTIONIST);
		rio = signUp("rio@grove.test", "Rio", "Park", Role.RECEPTIONIST);
		lead = signUp("lee@grove.test", "Lee", "Moss", Role.LEAD);
		stylist = signUp("ava@grove.test", "Ava", "Stone", null);
	}

	@Test
	void ownersAndGeneralManagersAssignToManagersAndReceptionists() throws Exception {
		for (Cookie boss : List.of(owner, gm)) {
			assign(boss, "Order towels " + boss.getValue(), status().isCreated(), "manny@grove.test", "rae@grove.test");
			assign(boss, "No", status().isForbidden(), "ava@grove.test");
			assign(boss, "No", status().isForbidden(), "lee@grove.test");
			assign(boss, "No", status().isForbidden(), "gm@grove.test");
			assign(boss, "No", status().isForbidden(), "owner@grove.test");
		}
		mvc.perform(get("/api/todos/assign-options").cookie(gm))
			.andExpect(jsonPath("$.people.length()").value(4))
			.andExpect(jsonPath("$.people[0].name").value("Manny Reed"))
			.andExpect(jsonPath("$.people[0].role").value("MANAGER"));
	}

	@Test
	void managersAssignOnlyToReceptionists() throws Exception {
		assign(manager, "Wipe the counter", status().isCreated(), "rae@grove.test");
		assign(manager, "No", status().isForbidden(), "mo@grove.test");
		assign(manager, "No", status().isForbidden(), "manny@grove.test");
		assign(manager, "No", status().isForbidden(), "ava@grove.test");
		mvc.perform(get("/api/todos/assign-options").cookie(manager))
			.andExpect(jsonPath("$.people.length()").value(2))
			.andExpect(jsonPath("$.people[0].name").value("Rae Hill"))
			.andExpect(jsonPath("$.people[1].name").value("Rio Park"));
	}

	@Test
	void everyoneElseCannotAssign() throws Exception {
		for (Cookie who : List.of(rae, lead, stylist)) {
			assign(who, "No", status().isForbidden(), "rio@grove.test");
			mvc.perform(get("/api/todos/assign-options").cookie(who)).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/todos/mine")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void oneBadPersonMeansNoOneGetsIt() throws Exception {
		assign(manager, "Sweep", status().isForbidden(), "rae@grove.test", "mo@grove.test");
		assertThat(assignments.findAll()).isEmpty();
		assertThat(items.findAll()).isEmpty();
	}

	@Test
	void anAssignmentNeedsTextAndPeople() throws Exception {
		assign(manager, "   ", status().isBadRequest(), "rae@grove.test");
		assign(manager, "Sweep", status().isBadRequest());
		mvc.perform(post("/api/todos").cookie(manager).contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"Sweep\",\"userIds\":[999999]}"))
			.andExpect(status().isBadRequest());
		// Someone deactivated can't be assigned to.
		var u = users.findByEmail("rae@grove.test").orElseThrow();
		u.setActive(false);
		users.save(u);
		assign(manager, "Sweep", status().isBadRequest(), "rae@grove.test");
	}

	@Test
	void assigningToSeveralGivesEachTheirOwnAndShowsWhoFinished() throws Exception {
		assign(manager, "Restock retail", status().isCreated(), "rae@grove.test", "rio@grove.test");

		String mine = mvc.perform(get("/api/todos/mine").cookie(rae))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].text").value("Restock retail"))
			.andExpect(jsonPath("$[0].assignedByName").value("Manny Reed"))
			.andExpect(jsonPath("$[0].completedAt").value(nullValue()))
			.andReturn().getResponse().getContentAsString();
		long id = Long.parseLong(mine.replaceAll(".*?\"id\":(\\d+).*", "$1"));

		mvc.perform(put("/api/todos/" + id + "/done").cookie(rae))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completedAt").isNotEmpty());

		// Rio's copy is still open; Manny sees that, and when Rae finished. Done ones sort last.
		mvc.perform(get("/api/todos/mine").cookie(rio)).andExpect(jsonPath("$[0].completedAt").value(nullValue()));
		mvc.perform(get("/api/todos/assigned").cookie(manager))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].assigneeName").value("Rio Park"))
			.andExpect(jsonPath("$[0].completedAt").value(nullValue()))
			.andExpect(jsonPath("$[1].assigneeName").value("Rae Hill"))
			.andExpect(jsonPath("$[1].completedAt").isNotEmpty());
		// Other assigners don't see it.
		mvc.perform(get("/api/todos/assigned").cookie(manager2)).andExpect(jsonPath("$.length()").value(0));

		// A mistaken check can be undone.
		mvc.perform(put("/api/todos/" + id + "/not-done").cookie(rae)).andExpect(jsonPath("$.completedAt").value(nullValue()));
	}

	@Test
	void onlyTheAssigneeChecksItOffAndOnlyTheAssignerTakesItBack() throws Exception {
		assign(manager, "Sweep", status().isCreated(), "rae@grove.test");
		long id = assignments.findAll().get(0).getId();
		for (Cookie other : List.of(manager, owner, rio)) {
			mvc.perform(put("/api/todos/" + id + "/done").cookie(other)).andExpect(status().isNotFound());
		}
		for (Cookie other : List.of(rae, owner, manager2)) {
			mvc.perform(delete("/api/todos/" + id).cookie(other)).andExpect(status().isNotFound());
		}
		mvc.perform(delete("/api/todos/" + id).cookie(manager)).andExpect(status().isNoContent());
		mvc.perform(get("/api/todos/mine").cookie(rae)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void assignedItemsAreSavedForTheDropdownWithoutDuplicates() throws Exception {
		assign(manager, "  Restock retail ", status().isCreated(), "rae@grove.test");
		// The same words in a different case reuse the saved one, for any assigner.
		assign(gm, "RESTOCK RETAIL", status().isCreated(), "rio@grove.test");
		assign(owner, "Backup the register", status().isCreated(), "rio@grove.test");

		mvc.perform(get("/api/todos/assign-options").cookie(manager))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].text").value("Backup the register"))
			.andExpect(jsonPath("$.items[1].text").value("Restock retail"));
		mvc.perform(get("/api/todos/mine").cookie(rio)).andExpect(jsonPath("$[0].text").value("Restock retail"));

		// Removing from the dropdown leaves it on lists. Only assigners can.
		long saved = items.findFirstByTextIgnoreCase("Restock retail").orElseThrow().getId();
		mvc.perform(delete("/api/todos/items/" + saved).cookie(rae)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/todos/items/" + saved).cookie(manager)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/todos/items/" + saved).cookie(manager)).andExpect(status().isNotFound());
		mvc.perform(get("/api/todos/assign-options").cookie(manager)).andExpect(jsonPath("$.items.length()").value(1));
		mvc.perform(get("/api/todos/mine").cookie(rae)).andExpect(jsonPath("$[0].text").value("Restock retail"));
	}

	@Test
	void someoneWithItStillOpenIsNotGivenItAgain() throws Exception {
		assign(manager, "Sweep", status().isCreated(), "rae@grove.test");
		assign(manager, "Sweep", status().isConflict(), "rae@grove.test");
		// Rio still gets it, Rae doesn't get a second copy.
		assign(manager, "Sweep", status().isCreated(), "rae@grove.test", "rio@grove.test");
		assertThat(assignments.findAll()).hasSize(2);

		// Once it's done it can be assigned again, like a daily chore.
		long raes = assignments.findAll().stream()
			.filter(t -> t.getAssigneeId().equals(userId("rae@grove.test"))).findFirst().orElseThrow().getId();
		mvc.perform(put("/api/todos/" + raes + "/done").cookie(rae)).andExpect(status().isOk());
		assign(manager, "Sweep", status().isCreated(), "rae@grove.test");
	}

	@Test
	void deletingAnAccountClearsTheirListAndKeepsWhatTheyAssigned() throws Exception {
		assign(manager, "Sweep", status().isCreated(), "rae@grove.test");
		assign(gm, "Mop", status().isCreated(), "manny@grove.test");
		mvc.perform(delete("/api/admin/team/" + userId("manny@grove.test")).cookie(owner)).andExpect(status().is2xxSuccessful());
		assertThat(assignments.findAll()).hasSize(1);
		mvc.perform(get("/api/todos/mine").cookie(rae)).andExpect(jsonPath("$[0].assignedByName").value("Former employee"));
	}

	private void assign(Cookie who, String text, ResultMatcher expected, String... emails) throws Exception {
		String ids = List.of(emails).stream().map(e -> String.valueOf(userId(e))).collect(Collectors.joining(","));
		mvc.perform(post("/api/todos").cookie(who).contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"" + text + "\",\"userIds\":[" + ids + "]}"))
			.andExpect(expected);
	}

	private Long userId(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private Cookie signUp(String email, String first, String last, Role role) throws Exception {
		Cookie cookie = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		if (role != null) {
			var u = users.findByEmail(email).orElseThrow();
			u.setRoles(role);
			users.save(u);
		}
		return cookie;
	}
}
