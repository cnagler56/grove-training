package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.grove.Domain.Role;
import com.grove.Domain.VacationPayRequest;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.VacationPayRequestRepository;
import com.grove.Service.Mailer;
import com.grove.Service.SessionService;
import com.grove.Service.VacationPayService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VacationPayTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired VacationPayRequestRepository requests;
	@Autowired VacationPayService service;
	@MockBean JavaMailSender mailSender;
	@SpyBean Mailer mailer;

	private Cookie owner;
	private Cookie ava;
	private LocalDate firstCheck;

	@BeforeEach
	void setUp() throws Exception {
		requests.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		firstCheck = service.checkDates().get(0);
	}

	@Test
	void checkDatesAreEveryTwoWeeksAndStillToCome() throws Exception {
		assertThat(dates(LocalDate.of(2026, 11, 1))).startsWith(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 27)).hasSize(8);
		// The day before a check it's still offered; on the day it's gone.
		assertThat(dates(LocalDate.of(2026, 11, 12)).get(0)).isEqualTo(LocalDate.of(2026, 11, 13));
		assertThat(dates(LocalDate.of(2026, 11, 13)).get(0)).isEqualTo(LocalDate.of(2026, 11, 27));
		assertThat(dates(LocalDate.of(2026, 11, 14)).get(0)).isEqualTo(LocalDate.of(2026, 11, 27));
		assertThat(dates(LocalDate.of(2026, 11, 27)).get(0)).isEqualTo(LocalDate.of(2026, 12, 11));

		mvc.perform(get("/api/vacation-pay/check-dates").cookie(ava))
			.andExpect(jsonPath("$.length()").value(8))
			.andExpect(jsonPath("$[0]").value(firstCheck.toString()));
		mvc.perform(get("/api/vacation-pay/check-dates")).andExpect(status().isUnauthorized());
	}

	@Test
	void aRequestShowsUpForOwnersAndTheAnswerGoesBackToTheEmployee() throws Exception {
		long id = request(ava, "16", firstCheck);

		mvc.perform(get("/api/vacation-pay/queue").cookie(owner))
			.andExpect(jsonPath("$.waiting.length()").value(1))
			.andExpect(jsonPath("$.waiting[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.waiting[0].hours").value(16))
			.andExpect(jsonPath("$.waiting[0].checkDate").value(firstCheck.toString()));
		mvc.perform(get("/api/vacation-pay/mine").cookie(ava))
			.andExpect(jsonPath("$[0].status").value("PENDING"));

		mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(owner))
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.decidedByName").value("Owner Person"));
		mvc.perform(get("/api/vacation-pay/queue").cookie(owner))
			.andExpect(jsonPath("$.waiting.length()").value(0))
			.andExpect(jsonPath("$.approved.length()").value(1));

		// Answered already: approving again is refused, undoing it needs a message.
		mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(owner)).andExpect(status().isBadRequest());
		mvc.perform(put("/api/vacation-pay/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"  \"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/vacation-pay/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"Only 8 hours are left.\"}"))
			.andExpect(jsonPath("$.status").value("SENT_BACK"));
		mvc.perform(get("/api/vacation-pay/mine").cookie(ava))
			.andExpect(jsonPath("$[0].status").value("SENT_BACK"))
			.andExpect(jsonPath("$[0].responseNote").value("Only 8 hours are left."));
		// A sent-back request doesn't block asking again for the same check.
		request(ava, "8", firstCheck);
	}

	@Test
	void hoursMustBeAPositiveNumber() throws Exception {
		send(ava, "0", firstCheck, status().isBadRequest());
		send(ava, "-4", firstCheck, status().isBadRequest());
		send(ava, "400.5", firstCheck, status().isBadRequest());
		send(ava, "1.125", firstCheck, status().isBadRequest());
		send(ava, "\"lots\"", firstCheck, status().isBadRequest());
		mvc.perform(post("/api/vacation-pay").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"checkDate\":\"" + firstCheck + "\"}"))
			.andExpect(status().isBadRequest());
		send(ava, "7.5", firstCheck, status().isCreated());
	}

	@Test
	void theCheckDateMustBeOnTheScheduleAndUpcoming() throws Exception {
		List<LocalDate> offered = service.checkDates();
		send(ava, "8", firstCheck.plusDays(7), status().isBadRequest());
		send(ava, "8", firstCheck.plusDays(1), status().isBadRequest());
		send(ava, "8", firstCheck.minusDays(14), status().isBadRequest());
		send(ava, "8", offered.get(offered.size() - 1).plusDays(14), status().isBadRequest());
		mvc.perform(post("/api/vacation-pay").cookie(ava).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":8}"))
			.andExpect(status().isBadRequest());
		send(ava, "8", offered.get(offered.size() - 1), status().isCreated());
	}

	@Test
	void oneOpenRequestPerCheckAndCancellingFreesItUp() throws Exception {
		long id = request(ava, "8", firstCheck);
		send(ava, "4", firstCheck, status().isConflict());
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(owner)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(ava)).andExpect(status().isNoContent());
		long again = request(ava, "4", firstCheck);
		mvc.perform(put("/api/vacation-pay/" + again + "/approve").cookie(owner)).andExpect(status().isOk());
		// An approved one still blocks a second.
		send(ava, "4", firstCheck, status().isConflict());
	}

	@Test
	void anApprovedRequestCanBeCancelledUntilTheCheckGoesOut() throws Exception {
		long id = request(ava, "8", firstCheck);
		mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(owner)).andExpect(status().isOk());
		// Only by the person who asked.
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(owner)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(ava)).andExpect(status().isNoContent());
		mvc.perform(get("/api/vacation-pay/mine").cookie(ava)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/vacation-pay/queue").cookie(owner)).andExpect(jsonPath("$.approved.length()").value(0));
		// Owners are told it came off, and it can be asked for again.
		verify(mailer).send(eq(List.of("owner@grove.test")), contains("Approved paid time off cancelled"), contains("Ava Stone"));
		request(ava, "8", firstCheck);
	}

	@Test
	void cancellingAWaitingRequestDoesNotBotherOwnersWithACancelNotice() throws Exception {
		long id = request(ava, "8", firstCheck);
		mvc.perform(delete("/api/vacation-pay/" + id).cookie(ava)).andExpect(status().isNoContent());
		verify(mailer, never()).send(anyList(), contains("cancelled"), anyString());
	}

	@Test
	void onceTheCheckIsOutOrItWasSentBackItCannotBeCancelled() throws Exception {
		long id = request(ava, "8", firstCheck);
		mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(owner)).andExpect(status().isOk());
		for (LocalDate gone : List.of(LocalDate.now(ZoneId.of("America/Chicago")), LocalDate.now(ZoneId.of("America/Chicago")).minusDays(14))) {
			VacationPayRequest r = requests.findById(id).orElseThrow();
			r.setCheckDate(gone);
			requests.save(r);
			mvc.perform(delete("/api/vacation-pay/" + id).cookie(ava)).andExpect(status().isBadRequest());
		}
		assertThat(requests.findById(id)).isPresent();

		long sentBack = request(ava, "8", firstCheck);
		mvc.perform(put("/api/vacation-pay/" + sentBack + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"No\"}"))
			.andExpect(status().isOk());
		mvc.perform(delete("/api/vacation-pay/" + sentBack).cookie(ava)).andExpect(status().isBadRequest());
	}

	@Test
	void onlyOwnersSeeAndAnswerRequests() throws Exception {
		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		long id = request(ava, "8", firstCheck);

		for (Cookie notOwner : List.of(ava, gm)) {
			mvc.perform(get("/api/vacation-pay/queue").cookie(notOwner)).andExpect(status().isForbidden());
			mvc.perform(put("/api/vacation-pay/" + id + "/approve").cookie(notOwner)).andExpect(status().isForbidden());
			mvc.perform(put("/api/vacation-pay/" + id + "/send-back").cookie(notOwner).contentType(MediaType.APPLICATION_JSON)
					.content("{\"message\":\"No\"}"))
				.andExpect(status().isForbidden());
		}
		// Your own list is only your own.
		mvc.perform(get("/api/vacation-pay/mine").cookie(gm)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/vacation-pay/queue")).andExpect(status().isUnauthorized());
	}

	@Test
	void onlyPeopleOnTheScheduleCanAsk() throws Exception {
		send(owner, "8", firstCheck, status().isForbidden());
		Cookie customer = signUp("cal@grove.test", "Cal", "Fox");
		setRole("cal@grove.test", Role.CUSTOMER);
		send(customer, "8", firstCheck, status().isForbidden());
		Cookie receptionist = signUp("rae@grove.test", "Rae", "Hill");
		setRole("rae@grove.test", Role.RECEPTIONIST);
		send(receptionist, "8", firstCheck, status().isCreated());
	}

	@Test
	void deletingAnAccountRemovesTheirRequests() throws Exception {
		request(ava, "8", firstCheck);
		Long avaId = users.findByEmail("ava@grove.test").orElseThrow().getUserId();
		mvc.perform(delete("/api/admin/team/" + avaId).cookie(owner)).andExpect(status().is2xxSuccessful());
		assertThat(requests.findAll()).isEmpty();
	}

	/** The check dates as they'd be offered on a given day. */
	private static List<LocalDate> dates(LocalDate today) {
		return VacationPayService.checkDatesAfter(today);
	}

	private long request(Cookie who, String hours, LocalDate checkDate) throws Exception {
		String body = mvc.perform(post("/api/vacation-pay").cookie(who).contentType(MediaType.APPLICATION_JSON)
				.content(json(hours, checkDate)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
	}

	private void send(Cookie who, String hours, LocalDate checkDate, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
		mvc.perform(post("/api/vacation-pay").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json(hours, checkDate)))
			.andExpect(expected);
	}

	private static String json(String hours, LocalDate checkDate) {
		return "{\"hours\":" + hours + ",\"checkDate\":\"" + checkDate + "\"}";
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
