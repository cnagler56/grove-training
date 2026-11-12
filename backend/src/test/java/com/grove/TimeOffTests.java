package com.grove;

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
import com.grove.Domain.TimeOffRequest;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.ScheduleNoteRepository;
import com.grove.Repository.ScheduleWeekRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.TimeOffRequestRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Service.Mailer;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;
import com.grove.Service.TimeOffService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimeOffTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired TimeOffRequestRepository requests;
	@Autowired com.grove.Repository.BlackoutDateRepository blackouts;
	@Autowired ScheduleNoteRepository scheduleNotes;
	@Autowired ShiftRepository shifts;
	@Autowired ScheduleWeekRepository weeks;
	@Autowired AppSettingRepository settings;
	@MockBean JavaMailSender mailSender;
	@SpyBean Mailer mailer;

	/** A Saturday safely in the future. */
	private final LocalDate week = ScheduleService.weekStart(LocalDate.now().plusDays(30));
	private Cookie owner;
	private Cookie ava;
	private Long avaId;

	@BeforeEach
	void setUp() throws Exception {
		requests.deleteAll();
		blackouts.deleteAll();
		scheduleNotes.deleteAll();
		shifts.deleteAll();
		weeks.deleteAll();
		settings.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		avaId = users.findByEmail("ava@grove.test").orElseThrow().getUserId();
	}

	@Test
	void approvedDaysShowAsRoOnTheSchedule() throws Exception {
		long id = request(ava, week.plusDays(2), week.plusDays(3), "Family trip");

		// Waiting: on the approver's list, not yet on the schedule.
		mvc.perform(get("/api/time-off/queue").cookie(owner))
			.andExpect(jsonPath("$.waiting.length()").value(1))
			.andExpect(jsonPath("$.waiting[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.waiting[0].note").value("Family trip"));
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(0));

		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(owner))
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.decidedByName").value("Owner Person"));

		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(2))
			.andExpect(jsonPath("$.shifts[0].type").value("REQUESTED_OFF"))
			.andExpect(jsonPath("$.shifts[0].note").value(TimeOffService.APPROVED_NOTE))
			.andExpect(jsonPath("$.shifts[0].date").value(week.plusDays(2).toString()))
			.andExpect(jsonPath("$.timeOff.length()").value(2));

		// Staff still see nothing until the week is posted, then the RO days.
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(ava))
			.andExpect(jsonPath("$.shifts.length()").value(0));
		mvc.perform(put("/api/schedule/" + week + "/published").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"published\":true}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(ava))
			.andExpect(jsonPath("$.shifts.length()").value(2));
	}

	@Test
	void approvedTimeOffCanBeCancelledAndComesOffTheSchedule() throws Exception {
		long id = request(ava, week.plusDays(2), week.plusDays(3), null);
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(owner)).andExpect(status().isOk());
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(2));

		// Only by the person who asked.
		mvc.perform(delete("/api/time-off/" + id).cookie(owner)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/time-off/" + id).cookie(ava)).andExpect(status().isNoContent());

		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(0))
			.andExpect(jsonPath("$.timeOff.length()").value(0));
		mvc.perform(get("/api/time-off/mine").cookie(ava)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/time-off/queue").cookie(owner)).andExpect(jsonPath("$.approved.length()").value(0));
		verify(mailer).send(eq(List.of("owner@grove.test")), contains("Approved time off cancelled"), contains("Ava Stone"));

		// The days can be asked for again.
		request(ava, week.plusDays(2), week.plusDays(3), null);
	}

	@Test
	void approvedTimeOffCanBeCancelledThroughItsFirstDayOnly() throws Exception {
		LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
		long started = request(ava, week, week, null);
		mvc.perform(put("/api/time-off/" + started + "/approve").cookie(owner)).andExpect(status().isOk());
		TimeOffRequest r = requests.findById(started).orElseThrow();
		r.setStartDate(today.minusDays(1));
		r.setEndDate(today.plusDays(1));
		requests.save(r);
		mvc.perform(delete("/api/time-off/" + started).cookie(ava)).andExpect(status().isBadRequest());

		r.setStartDate(today);
		requests.save(r);
		mvc.perform(delete("/api/time-off/" + started).cookie(ava)).andExpect(status().isNoContent());
	}

	@Test
	void aSentBackRequestCannotBeCancelledAndAWaitingOneSendsNoNotice() throws Exception {
		long waiting = request(ava, week, week, null);
		mvc.perform(delete("/api/time-off/" + waiting).cookie(ava)).andExpect(status().isNoContent());
		verify(mailer, never()).send(anyList(), contains("cancelled"), anyString());

		long sentBack = request(ava, week, week, null);
		mvc.perform(put("/api/time-off/" + sentBack + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"No\"}"))
			.andExpect(status().isOk());
		mvc.perform(delete("/api/time-off/" + sentBack).cookie(ava)).andExpect(status().isBadRequest());
	}

	@Test
	void builderNotesNeverReachStaff() throws Exception {
		mvc.perform(put("/api/schedule/" + week).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shifts\":[" + shift(week, "10:00", "18:00") + "],\"notes\":{\"" + avaId + "\":\"Ask about Tuesday\"}}"))
			.andExpect(jsonPath("$.builderNotes['" + avaId + "']").value("Ask about Tuesday"));
		mvc.perform(put("/api/schedule/" + week + "/published").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"published\":true}"))
			.andExpect(status().isOk());

		// Posted, staff get the shifts but no notes, not even in the raw response.
		String staffSees = mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(ava))
			.andExpect(jsonPath("$.shifts.length()").value(1))
			.andExpect(jsonPath("$.builderNotes").isEmpty())
			.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(staffSees).doesNotContain("Ask about Tuesday");
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.builderNotes['" + avaId + "']").value("Ask about Tuesday"));

		// Saving without notes keeps them; a blank note removes it.
		save(week, shift(week, "10:00", "17:00"));
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.builderNotes['" + avaId + "']").value("Ask about Tuesday"));
		mvc.perform(put("/api/schedule/" + week).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shifts\":[],\"notes\":{\"" + avaId + "\":\"  \"}}"))
			.andExpect(jsonPath("$.builderNotes").isEmpty());
	}

	@Test
	void anExistingShiftIsKeptAndTheDayIsStillListed() throws Exception {
		LocalDate day = week.plusDays(2);
		save(week, shift(day, "10:00", "18:00"));
		long id = request(ava, day, day, null);
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(owner)).andExpect(status().isOk());

		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(1))
			.andExpect(jsonPath("$.shifts[0].type").value("WORK"))
			.andExpect(jsonPath("$.timeOff[0].date").value(day.toString()));
	}

	@Test
	void sendingBackNeedsAMessageTheEmployeeCanSee() throws Exception {
		long id = request(ava, week.plusDays(2), week.plusDays(2), null);
		mvc.perform(put("/api/time-off/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"  \"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/time-off/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"We're short that day. Can you do Tuesday?\"}"))
			.andExpect(jsonPath("$.status").value("SENT_BACK"));

		mvc.perform(get("/api/time-off/mine").cookie(ava))
			.andExpect(jsonPath("$[0].status").value("SENT_BACK"))
			.andExpect(jsonPath("$[0].responseNote").value("We're short that day. Can you do Tuesday?"));
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(0));
		// A sent-back request doesn't block asking again for the same day.
		request(ava, week.plusDays(2), week.plusDays(2), "Second try");
	}

	@Test
	void undoingAnApprovalTakesTheSavedRoDaysBackOff() throws Exception {
		LocalDate day = week.plusDays(2);
		long id = request(ava, day, day, null);
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(owner)).andExpect(status().isOk());
		// The builder saves the week, which stores the RO day.
		save(week, "{\"userId\":" + avaId + ",\"date\":\"" + day + "\",\"type\":\"REQUESTED_OFF\",\"note\":\"" + TimeOffService.APPROVED_NOTE + "\"}");
		mvc.perform(put("/api/time-off/" + id + "/send-back").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"Sorry, we need you after all.\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/schedule").param("date", week.toString()).cookie(owner))
			.andExpect(jsonPath("$.shifts.length()").value(0));
	}

	@Test
	void badDatesAndOverlapsAreRefused() throws Exception {
		LocalDate today = LocalDate.now();
		send(ava, today.minusDays(1), today, status().isBadRequest());
		send(ava, week.plusDays(3), week.plusDays(2), status().isBadRequest());
		send(ava, week, week.plusDays(70), status().isBadRequest());
		long id = request(ava, week, week.plusDays(1), null);
		send(ava, week.plusDays(1), week.plusDays(2), status().isConflict());
		// Cancelling frees those days up.
		mvc.perform(delete("/api/time-off/" + id).cookie(ava)).andExpect(status().isNoContent());
		request(ava, week.plusDays(1), week.plusDays(2), null);
	}

	@Test
	void theOwnerDecidesWhichRolesApprove() throws Exception {
		Cookie manager = signUp("max@grove.test", "Max", "Lee");
		setRole("max@grove.test", Role.MANAGER);
		long id = request(ava, week, week, null);

		mvc.perform(get("/api/time-off/queue").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(get("/api/time-off/queue").cookie(manager)).andExpect(status().isForbidden());

		mvc.perform(put("/api/owner/permissions").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":{\"APPROVE_TIME_OFF\":[\"MANAGER\"]}}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/me/permissions").cookie(manager)).andExpect(jsonPath("$.approveTimeOff").value(true));
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(manager)).andExpect(status().isOk());
	}

	@Test
	void nobodyAnswersTheirOwnRequestAndOnlyScheduledStaffCanAsk() throws Exception {
		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		long id = request(gm, week, week, null);
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(get("/api/time-off/queue").cookie(gm)).andExpect(jsonPath("$.waiting.length()").value(0));
		mvc.perform(put("/api/time-off/" + id + "/approve").cookie(owner)).andExpect(status().isOk());

		send(owner, week, week, status().isForbidden());
		Cookie customer = signUp("cal@grove.test", "Cal", "Fox");
		setRole("cal@grove.test", Role.CUSTOMER);
		send(customer, week, week, status().isForbidden());
	}

	private String shift(LocalDate day, String start, String end) {
		return "{\"userId\":" + avaId + ",\"date\":\"" + day + "\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
	}

	private void save(LocalDate start, String shiftJson) throws Exception {
		mvc.perform(put("/api/schedule/" + start).cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shifts\":[" + shiftJson + "]}"))
			.andExpect(status().isOk());
	}

	@Test
	void blackedOutDaysCantBeRequestedAndShowOnTheCalendar() throws Exception {
		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		setRole("gm@grove.test", Role.GENERAL_MANAGER);
		LocalDate holiday = week.plusDays(4);
		long earlier = request(ava, week.plusDays(1), week.plusDays(1), null);

		// Only time off approvers black out days and see the calendar.
		mvc.perform(put("/api/time-off/blackouts/" + holiday).cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"x\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/time-off/calendar").param("from", week.toString()).param("to", week.plusDays(6).toString()).cookie(ava))
			.andExpect(status().isForbidden());
		mvc.perform(put("/api/time-off/blackouts/" + holiday).cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"Holiday rush\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.note").value("Holiday rush"));

		// A request touching it is turned away, naming the day; one around it is fine.
		mvc.perform(post("/api/time-off").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content(json(week.plusDays(3), week.plusDays(5), null)))
			.andExpect(status().isBadRequest())
			.andExpect(status().reason(org.hamcrest.Matchers.containsString("blacked out")));
		request(ava, week.plusDays(5), week.plusDays(5), null);

		// Everyone can see upcoming blackouts; approvers see them on the calendar with the time off.
		mvc.perform(get("/api/time-off/blackouts").cookie(ava))
			.andExpect(jsonPath("$[0].date").value(holiday.toString()))
			.andExpect(jsonPath("$[0].note").value("Holiday rush"));
		mvc.perform(get("/api/time-off/calendar").param("from", week.toString()).param("to", week.plusDays(6).toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blackouts.length()").value(1))
			.andExpect(jsonPath("$.timeOff.length()").value(2))
			.andExpect(jsonPath("$.timeOff[0].id").value(earlier))
			.andExpect(jsonPath("$.timeOff[0].name").value("Ava Stone"));

		// Clearing it lets the day be asked for again.
		mvc.perform(delete("/api/time-off/blackouts/" + holiday).cookie(gm)).andExpect(status().isNoContent());
		request(ava, holiday, holiday, null);
		mvc.perform(get("/api/time-off/calendar").param("from", week.toString()).param("to", week.plusDays(200).toString()).cookie(owner))
			.andExpect(status().isBadRequest());
	}

	private long request(Cookie who, LocalDate start, LocalDate end, String note) throws Exception {
		String body = mvc.perform(post("/api/time-off").cookie(who).contentType(MediaType.APPLICATION_JSON)
				.content(json(start, end, note)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*?\"id\":(\\d+).*", "$1"));
	}

	private void send(Cookie who, LocalDate start, LocalDate end, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
		mvc.perform(post("/api/time-off").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json(start, end, null)))
			.andExpect(expected);
	}

	private static String json(LocalDate start, LocalDate end, String note) {
		return "{\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\""
			+ (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
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
