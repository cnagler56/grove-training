package com.grove;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.grove.Domain.Role;
import com.grove.Repository.BusinessContactRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactsTests {

	private static final String LANDLORD = "{\"name\":\"  Oak Street Properties \",\"phone\":\"(555) 555-0100\",\"type\":\" Landlord \"}";

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired BusinessContactRepository contacts;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private Cookie manager;
	private Cookie stylist;

	@BeforeEach
	void setUp() throws Exception {
		contacts.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		manager = signUp("manny@grove.test", "Manny", "Reed");
		setRole("manny@grove.test", Role.MANAGER);
		stylist = signUp("ava@grove.test", "Ava", "Stone");
	}

	@Test
	void aManagerAddsAContactAndItIsListedAToZ() throws Exception {
		mvc.perform(post("/api/admin/contacts").cookie(manager).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Oak Street Properties"))
			.andExpect(jsonPath("$.phone").value("555-555-0100"))
			.andExpect(jsonPath("$.type").value("Landlord"));
		mvc.perform(post("/api/admin/contacts").cookie(manager).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"color supply\",\"phone\":\"1-555-555-0111\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.phone").value("555-555-0111"))
			.andExpect(jsonPath("$.type").doesNotExist());

		mvc.perform(get("/api/admin/contacts").cookie(manager))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("color supply"))
			.andExpect(jsonPath("$[1].name").value("Oak Street Properties"));
	}

	@Test
	void aContactCanBeChanged() throws Exception {
		long id = add(manager);
		mvc.perform(put("/api/admin/contacts/" + id).cookie(manager).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Oak Street Realty\",\"phone\":\"555-555-0122\",\"type\":\"Landlord, ask for the office\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.name").value("Oak Street Realty"))
			.andExpect(jsonPath("$.phone").value("555-555-0122"))
			.andExpect(jsonPath("$.type").value("Landlord, ask for the office"));
		mvc.perform(get("/api/admin/contacts").cookie(manager))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Oak Street Realty"));
		mvc.perform(put("/api/admin/contacts/999999").cookie(manager).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
			.andExpect(status().isNotFound());
	}

	@Test
	void aContactCanBeDeleted() throws Exception {
		long id = add(manager);
		mvc.perform(delete("/api/admin/contacts/" + id).cookie(manager)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/admin/contacts/" + id).cookie(manager)).andExpect(status().isNotFound());
		mvc.perform(get("/api/admin/contacts").cookie(manager)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aContactNeedsANameAndARealPhoneNumber() throws Exception {
		for (String body : List.of(
				"{\"phone\":\"555-555-0100\"}",
				"{\"name\":\"  \",\"phone\":\"555-555-0100\"}",
				"{\"name\":\"Oak Street\"}",
				"{\"name\":\"Oak Street\",\"phone\":\"  \"}",
				"{\"name\":\"Oak Street\",\"phone\":\"555-0100\"}",
				"{\"name\":\"Oak Street\",\"phone\":\"call the office\"}",
				"{\"name\":\"Oak Street\",\"phone\":\"555-555-0100-22\"}")) {
			mvc.perform(post("/api/admin/contacts").cookie(manager).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/admin/contacts").cookie(manager)).andExpect(jsonPath("$.length()").value(0));

		// A bad edit leaves the saved contact as it was.
		long id = add(manager);
		mvc.perform(put("/api/admin/contacts/" + id).cookie(manager).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Oak Street\",\"phone\":\"12\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/admin/contacts").cookie(manager))
			.andExpect(jsonPath("$[0].name").value("Oak Street Properties"))
			.andExpect(jsonPath("$[0].phone").value("555-555-0100"));
	}

	@Test
	void ownersAndGeneralManagersCanKeepTheListToo() throws Exception {
		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		for (Cookie who : List.of(owner, gm)) {
			long id = add(who);
			mvc.perform(get("/api/admin/contacts").cookie(who)).andExpect(jsonPath("$.length()").value(1));
			mvc.perform(put("/api/admin/contacts/" + id).cookie(who).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
				.andExpect(status().isOk());
			mvc.perform(delete("/api/admin/contacts/" + id).cookie(who)).andExpect(status().isNoContent());
		}
	}

	@Test
	void everyoneBelowManagerIsRefused() throws Exception {
		long id = add(manager);
		Cookie receptionist = signUp("rae@grove.test", "Rae", "Hill");
		setRole("rae@grove.test", Role.RECEPTIONIST);
		for (Cookie who : List.of(stylist, receptionist)) {
			mvc.perform(get("/api/admin/contacts").cookie(who)).andExpect(status().isForbidden());
			mvc.perform(post("/api/admin/contacts").cookie(who).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
				.andExpect(status().isForbidden());
			mvc.perform(put("/api/admin/contacts/" + id).cookie(who).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
				.andExpect(status().isForbidden());
			mvc.perform(delete("/api/admin/contacts/" + id).cookie(who)).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/admin/contacts")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/contacts").cookie(manager)).andExpect(jsonPath("$.length()").value(1));
	}

	private long add(Cookie who) throws Exception {
		String body = mvc.perform(post("/api/admin/contacts").cookie(who).contentType(MediaType.APPLICATION_JSON).content(LANDLORD))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
	}

	private void setRole(String email, Role role) {
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		users.save(u);
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
