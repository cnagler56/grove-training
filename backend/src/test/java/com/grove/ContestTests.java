package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grove.Domain.Role;
import com.grove.Repository.ContestEntryRepository;
import com.grove.Repository.ContestRepository;
import com.grove.Repository.ContestTeamRepository;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Domain.WeeklyStat;
import com.grove.Service.ContestService;
import com.grove.Service.PushService;
import com.grove.Service.SalonUltimateClient;
import com.grove.Service.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContestTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired ContestRepository contests;
	@Autowired ContestEntryRepository entries;
	@Autowired ContestTeamRepository teams;
	@Autowired TeamPostRepository posts;
	@Autowired WeeklyStatRepository stats;
	@Autowired ContestService service;
	@MockBean SalonUltimateClient salonUltimate;
	@SpyBean PushService push;
	@MockBean JavaMailSender mailSender;

	private final LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
	private Cookie owner, manager, ava;
	private Long avaId, miaId, leeId, zoeId, raeId;

	@BeforeEach
	void setUp() throws Exception {
		entries.deleteAll();
		teams.deleteAll();
		stats.deleteAll();
		contests.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person", null, null);
		manager = signUp("manny@grove.test", "Manny", "Reed", Role.MANAGER, null);
		ava = signUp("ava@grove.test", "Ava", "Stone", null, "Ava Stone");
		signUp("mia@grove.test", "Mia", "Cruz", null, "Mia Cruz");
		signUp("lee@grove.test", "Lee", "Moss", null, "Lee Moss");
		signUp("zoe@grove.test", "Zoe", "Park", null, "Zoe Park");
		signUp("rae@grove.test", "Rae", "Hill", Role.RECEPTIONIST, null);
		avaId = id("ava@grove.test");
		miaId = id("mia@grove.test");
		leeId = id("lee@grove.test");
		zoeId = id("zoe@grove.test");
		raeId = id("rae@grove.test");
		when(salonUltimate.isConfigured()).thenReturn(true);
		report("Ava Stone", 0, "Mia Cruz", 0, "Lee Moss", 0, "Zoe Park", 0);
		clearInvocations(push);
	}

	@Test
	void everyoneOnStaffSeesContestsButOnlyManagersRunThem() throws Exception {
		long id = create(manager, contest("Treatment Blitz", List.of(), person(avaId, null), person(miaId, null)));
		mvc.perform(get("/api/contests").cookie(ava))
			.andExpect(jsonPath("$[0].name").value("Treatment Blitz"))
			.andExpect(jsonPath("$[0].status").value("LIVE"))
			.andExpect(jsonPath("$[0].metricLabel").value("treatments"))
			.andExpect(jsonPath("$[0].createdByName").value("Manny Reed"));
		send(ava, post("/api/contests"), contest("No", List.of(), person(avaId, null), person(miaId, null)), status().isForbidden());
		mvc.perform(get("/api/contests/people").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(post("/api/contests/" + id + "/check").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/contests/" + id).cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(get("/api/contests")).andExpect(status().isUnauthorized());
		// Only people linked to Salon Ultimate can be picked.
		mvc.perform(get("/api/contests/people").cookie(owner))
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].name").value("Ava Stone"));
	}

	@Test
	void receptionistsAreNotPartOfContestsEvenWhenLinked() throws Exception {
		var rae = users.findById(raeId).orElseThrow();
		rae.setSalonUltimateName("Rae Hill");
		users.save(rae);
		mvc.perform(get("/api/contests/people").cookie(owner))
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[?(@.name == 'Rae Hill')]").isEmpty());
		send(manager, post("/api/contests"), contest("Blitz", List.of(), person(avaId, null), person(miaId, null), person(raeId, null)),
			status().isBadRequest());
		assertThat(contests.findAll()).isEmpty();
	}

	@Test
	void aContestNeedsTheBasicsAndPeopleLinkedToSalonUltimate() throws Exception {
		Map<String, Object> ok = contest("Blitz", List.of(), person(avaId, null), person(miaId, null));
		bad(with(ok, "name", " "));
		bad(with(ok, "metric", null));
		bad(with(ok, "endDate", today.minusDays(1).toString()));
		bad(with(with(ok, "startDate", today.minusDays(3).toString()), "endDate", today.minusDays(1).toString()));
		bad(with(ok, "endDate", today.plusDays(60).toString()));
		bad(contest("Blitz", List.of(), person(avaId, null)));
		bad(contest("Blitz", List.of(), person(avaId, null), person(raeId, null)));
		// Teams: two or more, named, everyone on one, no empty team.
		bad(contest("Blitz", List.of("Oak"), person(avaId, 0), person(miaId, 0)));
		bad(contest("Blitz", List.of("Oak", " "), person(avaId, 0), person(miaId, 1)));
		bad(contest("Blitz", List.of("Oak", "oak"), person(avaId, 0), person(miaId, 1)));
		bad(contest("Blitz", List.of("Oak", "Pine"), person(avaId, 0), person(miaId, null)));
		bad(contest("Blitz", List.of("Oak", "Pine"), person(avaId, 0), person(miaId, 0)));
		assertThat(contests.findAll()).isEmpty();
		create(manager, ok);
		create(manager, contest("Teams", List.of("Oak", "Pine"), person(avaId, 0), person(miaId, 1)));
	}

	@Test
	void theFirstCheckIsQuietThenPeopleWhosePlaceChangedAreTold() throws Exception {
		long id = create(manager, contest("Treatment Blitz", List.of(), person(avaId, null), person(miaId, null), person(leeId, null)));
		report("Ava Stone", 2, "Mia Cruz", 1, "Lee Moss", 0);
		check(id)
			.andExpect(jsonPath("$.standings[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.standings[0].count").value(2))
			.andExpect(jsonPath("$.standings[0].rank").value(1))
			.andExpect(jsonPath("$.standings[2].name").value("Lee Moss"))
			.andExpect(jsonPath("$.standings[2].rank").value(3));
		verify(push, never()).notify(any(), anyString(), anyString(), anyString());

		// Only treatments count: Lee's colors don't help.
		report("Ava Stone", 2, "Mia Cruz", 4, "Lee Moss", 2, 50);
		check(id)
			.andExpect(jsonPath("$.standings[0].name").value("Mia Cruz"))
			.andExpect(jsonPath("$.standings[1].rank").value(2))
			.andExpect(jsonPath("$.standings[2].rank").value(2));
		verify(push).notify(eq(List.of(miaId)), eq("Treatment Blitz"), eq("You moved up to 1st with 4 treatments."), eq("/contests"));
		verify(push).notify(eq(List.of(avaId)), eq("Treatment Blitz"),
			eq("You're now tied for 2nd with 2 treatments. 2 more treatments to move up."), eq("/contests"));
		verify(push).notify(eq(List.of(leeId)), eq("Treatment Blitz"), contains("You moved up to tied for 2nd"), eq("/contests"));

		// No change, no notification.
		clearInvocations(push);
		check(id);
		verify(push, never()).notify(any(), anyString(), anyString(), anyString());
	}

	@Test
	void teamsAreTotalledAndEveryoneHearsWhenTheLeadChanges() throws Exception {
		long id = create(manager, contest("Team Blitz", List.of("Oak", "Pine"),
			person(avaId, 0), person(leeId, 0), person(miaId, 1), person(zoeId, 1)));
		report("Ava Stone", 3, "Lee Moss", 0, "Mia Cruz", 1, "Zoe Park", 0);
		check(id)
			.andExpect(jsonPath("$.teams[0].name").value("Oak"))
			.andExpect(jsonPath("$.teams[0].total").value(3))
			.andExpect(jsonPath("$.teams[0].rank").value(1))
			.andExpect(jsonPath("$.teams[0].members").value(2))
			.andExpect(jsonPath("$.teams[1].total").value(1))
			.andExpect(jsonPath("$.teams[1].rank").value(2));
		verify(push, never()).notify(any(), anyString(), anyString(), anyString());

		report("Ava Stone", 3, "Lee Moss", 0, "Mia Cruz", 5, "Zoe Park", 1);
		check(id).andExpect(jsonPath("$.teams[1].rank").value(1));
		verify(push).notify(argThat((Collection<Long> to) -> to.size() == 4 && to.containsAll(List.of(avaId, leeId, miaId, zoeId))),
			eq("Team Blitz"), eq("Pine took the lead, 6 to 3."), eq("/contests"));
	}

	@Test
	void afterTheLastDayTheResultsAreAnnouncedOnce() throws Exception {
		long id = create(manager, contest("Team Blitz", List.of("Oak", "Pine"), person(avaId, 0), person(miaId, 1)));
		report("Ava Stone", 3, "Mia Cruz", 1);
		check(id);
		var c = contests.findById(id).orElseThrow();
		c.setStartDate(today.minusDays(10));
		c.setEndDate(today.minusDays(1));
		contests.save(c);
		long postsBefore = posts.count();

		service.checkAll();
		verify(salonUltimate).detailedServiceByEmployee(today.minusDays(10), today.minusDays(1));
		mvc.perform(get("/api/contests").cookie(ava)).andExpect(jsonPath("$[0].status").value("FINISHED"));
		verify(push).notify(eq(List.of(avaId)), eq("Team Blitz is over"),
			eq("You finished 1st with 3 treatments. Oak won as a team."), eq("/contests"));
		assertThat(posts.count()).isEqualTo(postsBefore + 1);
		String body = posts.findAll().stream().map(p -> p.getBody()).filter(b -> b != null && b.startsWith("Team Blitz results"))
			.findFirst().orElseThrow();
		assertThat(body).contains("1st: Ava Stone, 3 treatments", "2nd: Mia Cruz, 1 treatment", "Oak: 3 treatments", "Oak wins!");

		// Finished: not checked again, can't be changed.
		clearInvocations(salonUltimate);
		service.checkAll();
		verify(salonUltimate, never()).detailedServiceByEmployee(any(), any());
		assertThat(posts.count()).isEqualTo(postsBefore + 1);
		send(manager, put("/api/contests/" + id), contest("Again", List.of(), person(avaId, null), person(miaId, null)), status().isBadRequest());
	}

	@Test
	void aProblemReadingSalonUltimateIsShownToManagersAndKeepsTheStandings() throws Exception {
		long id = create(manager, contest("Blitz", List.of(), person(avaId, null), person(miaId, null)));
		report("Ava Stone", 2, "Mia Cruz", 1);
		check(id);

		when(salonUltimate.detailedServiceByEmployee(any(), any()))
			.thenReturn("<p>There are unclosed time cards. Could Not Display Report</p>");
		check(id)
			.andExpect(jsonPath("$.checkProblem").value(containsString("timecards are closed")))
			.andExpect(jsonPath("$.standings[0].count").value(2));
		mvc.perform(get("/api/contests").cookie(ava)).andExpect(jsonPath("$[0].checkProblem").doesNotExist());

		when(salonUltimate.isConfigured()).thenReturn(false);
		check(id).andExpect(jsonPath("$.checkProblem").value(containsString("isn't set up")));
	}

	@Test
	void upcomingContestsAreNotChecked() throws Exception {
		Map<String, Object> later = with(with(contest("Later", List.of(), person(avaId, null), person(miaId, null)),
			"startDate", today.plusDays(3).toString()), "endDate", today.plusDays(9).toString());
		long id = create(manager, later);
		mvc.perform(get("/api/contests").cookie(ava)).andExpect(jsonPath("$[0].status").value("UPCOMING"));
		mvc.perform(post("/api/contests/" + id + "/check").cookie(manager)).andExpect(status().isBadRequest());
		service.checkAll();
		verify(salonUltimate, never()).detailedServiceByEmployee(any(), any());
	}

	@Test
	void editingKeepsTheStandingsUnlessWhatsCountedChanges() throws Exception {
		long id = create(manager, contest("Blitz", List.of("Oak", "Pine"), person(avaId, 0), person(miaId, 1)));
		report("Ava Stone", 2, "Mia Cruz", 1);
		check(id);

		// Renaming teams and adding someone keeps what's counted so far.
		send(manager, put("/api/contests/" + id),
			contest("Big Blitz", List.of("Oaks", "Pines"), person(avaId, 0), person(miaId, 1), person(leeId, 1)), status().isOk());
		mvc.perform(get("/api/contests").cookie(ava))
			.andExpect(jsonPath("$[0].name").value("Big Blitz"))
			.andExpect(jsonPath("$[0].teams[0].name").value("Oaks"))
			.andExpect(jsonPath("$[0].standings.length()").value(3))
			.andExpect(jsonPath("$[0].standings[0].count").value(2));

		// Counting colors instead starts the standings over.
		Map<String, Object> colors = with(contest("Big Blitz", List.of("Oaks", "Pines"), person(avaId, 0), person(miaId, 1)), "metric", "COLORS");
		send(manager, put("/api/contests/" + id), colors, status().isOk());
		mvc.perform(get("/api/contests").cookie(ava))
			.andExpect(jsonPath("$[0].metricLabel").value("colors"))
			.andExpect(jsonPath("$[0].standings.length()").value(2))
			.andExpect(jsonPath("$[0].standings[0].rank").doesNotExist());

		mvc.perform(delete("/api/contests/" + id).cookie(manager)).andExpect(status().isNoContent());
		assertThat(entries.findAll()).isEmpty();
		assertThat(teams.findAll()).isEmpty();
	}

	@Test
	void aHandicapIsOneHundredAtTheAverageOrBetterAndUpToTwoHundredBelowIt() {
		assertThat(ContestService.worth(10, 10.0)).isEqualTo(100);
		assertThat(ContestService.worth(10, 15.0)).isEqualTo(100);
		assertThat(ContestService.worth(32, 20.0)).isEqualTo(160);
		assertThat(ContestService.worth(10, 8.0)).isEqualTo(125);
		assertThat(ContestService.worth(10, 1.0)).isEqualTo(200);
		assertThat(ContestService.worth(10, 0.0)).isEqualTo(200);
		assertThat(ContestService.worth(10, null)).isEqualTo(100);
		assertThat(ContestService.worth(0, 5.0)).isEqualTo(100);
	}

	@Test
	void anHoursHandicapGivesPartTimersMorePointsFromTheFourWeeksBeforeTheStart() throws Exception {
		LocalDate startWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
		for (int w = 1; w <= 4; w++) {
			week("Ava Stone", startWeek.minusWeeks(w), 40, 50, 0);
			week("Mia Cruz", startWeek.minusWeeks(w), 20, 25, 0);
		}
		// Weeks outside the four before the start don't count.
		week("Mia Cruz", startWeek.minusWeeks(5), 0, 0, 0);
		week("Mia Cruz", startWeek, 0, 0, 0);

		long id = create(manager, with(contest("Blitz", List.of("Oak", "Pine"),
			person(avaId, 0), person(miaId, 1), person(leeId, 1)), "handicap", "HOURS"));
		report("Ava Stone", 3, "Mia Cruz", 2, "Lee Moss", 2);
		// Average 30 hours: Ava (40) is worth 100, Mia (20) 150, Lee (no weeks yet) 100.
		check(id)
			.andExpect(jsonPath("$.handicap").value("HOURS"))
			.andExpect(jsonPath("$.standings[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.standings[0].worth").value(100))
			.andExpect(jsonPath("$.standings[0].points").value(300))
			.andExpect(jsonPath("$.standings[0].rank").value(1))
			.andExpect(jsonPath("$.standings[1].name").value("Mia Cruz"))
			.andExpect(jsonPath("$.standings[1].worth").value(150))
			.andExpect(jsonPath("$.standings[1].points").value(300))
			.andExpect(jsonPath("$.standings[1].count").value(2))
			.andExpect(jsonPath("$.standings[1].rank").value(1))
			.andExpect(jsonPath("$.standings[2].worth").value(100))
			.andExpect(jsonPath("$.teams[0].total").value(300))
			.andExpect(jsonPath("$.teams[1].total").value(500));
	}

	@Test
	void aRateHandicapValuesServicesBySomeonesUsualRateAndStaysPutDuringTheContest() throws Exception {
		LocalDate startWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
		for (int w = 1; w <= 4; w++) {
			week("Ava Stone", startWeek.minusWeeks(w), 35, 10, 1);
			week("Mia Cruz", startWeek.minusWeeks(w), 35, 40, 1);
		}
		long id = create(manager, with(contest("Treatment Blitz", List.of(), person(avaId, null), person(miaId, null)), "handicap", "RATE"));
		// Ava: 1 in 10 guests. Mia: 1 in 40. Average 1 in 16: Ava 100, Mia 200 (the most).
		report("Ava Stone", 1, "Mia Cruz", 0);
		check(id)
			.andExpect(jsonPath("$.standings[0].worth").value(100))
			.andExpect(jsonPath("$.standings[1].worth").value(200));

		report("Ava Stone", 3, "Mia Cruz", 2);
		check(id)
			.andExpect(jsonPath("$.standings[0].name").value("Mia Cruz"))
			.andExpect(jsonPath("$.standings[0].points").value(400));
		verify(push).notify(eq(List.of(miaId)), eq("Treatment Blitz"), eq("You moved up to 1st with 400 points (2 treatments)."), eq("/contests"));
		verify(push).notify(eq(List.of(avaId)), eq("Treatment Blitz"),
			eq("You're now 2nd with 300 points (3 treatments). 1 more treatment to move up."), eq("/contests"));

		// A new week of stats mid-contest doesn't change anyone's value.
		stats.findAll().stream().filter(s -> s.getSuName().equals("Ava Stone")).forEach(s -> { s.setTreatments(0); stats.save(s); });
		check(id).andExpect(jsonPath("$.standings[1].worth").value(100));

		// Turning the handicap off starts the standings over, counting 1 each.
		send(manager, put("/api/contests/" + id), contest("Treatment Blitz", List.of(), person(avaId, null), person(miaId, null)), status().isOk());
		check(id)
			.andExpect(jsonPath("$.handicap").value("NONE"))
			.andExpect(jsonPath("$.standings[0].name").value("Ava Stone"))
			.andExpect(jsonPath("$.standings[0].points").value(3))
			.andExpect(jsonPath("$.standings[0].worth").doesNotExist());
	}

	@Test
	void deletingAnAccountTakesThemOutOfContests() throws Exception {
		create(manager, contest("Blitz", List.of(), person(avaId, null), person(miaId, null), person(leeId, null)));
		mvc.perform(delete("/api/admin/team/" + leeId).cookie(owner)).andExpect(status().is2xxSuccessful());
		assertThat(entries.findAll()).hasSize(2);
	}

	/** Salon Ultimate's Detailed Service page with these treatment counts ("Name", count, ...), plus colors for the last person when given. */
	private void report(Object... pairs) throws Exception {
		StringBuilder html = new StringBuilder("<table>");
		int extraColors = pairs.length % 2 == 1 ? (Integer) pairs[pairs.length - 1] : 0;
		int people = pairs.length / 2;
		for (int i = 0; i < people; i++) {
			html.append("<tr><td>Service Provider: ").append(pairs[i * 2]).append("</td></tr>")
				.append("<tr><td>Category</td><td>Service Code</td><td>Service Description</td><td>Total Units</td><td>Total Dollars</td></tr>")
				.append("<tr><td>Treatment</td></tr>")
				.append("<tr><td></td><td>EMT</td><td>Oil Treatment</td><td>").append(pairs[i * 2 + 1]).append("</td><td>0</td></tr>");
			if (i == people - 1 && extraColors > 0) {
				html.append("<tr><td>Color</td></tr><tr><td></td><td>AOCC</td><td>All over Color</td><td>")
					.append(extraColors).append("</td><td>0</td></tr>");
			}
		}
		when(salonUltimate.detailedServiceByEmployee(any(), any())).thenReturn(html.append("</table>").toString());
	}

	/** One finished week of Leaderboards stats for a Salon Ultimate name. */
	private void week(String suName, LocalDate weekStart, int hours, int guests, int treatments) {
		WeeklyStat s = stats.findByWeekStart(weekStart).stream().filter(x -> x.getSuName().equals(suName)).findFirst().orElseGet(WeeklyStat::new);
		s.setWeekStart(weekStart);
		s.setSuName(suName);
		s.setFloorHours(BigDecimal.valueOf(hours));
		s.setClients(guests);
		s.setTreatments(treatments);
		stats.save(s);
	}

	private org.springframework.test.web.servlet.ResultActions check(long id) throws Exception {
		return mvc.perform(post("/api/contests/" + id + "/check").cookie(manager)).andExpect(status().isOk());
	}

	@SafeVarargs
	private Map<String, Object> contest(String name, List<String> teamNames, Map<String, Object>... people) {
		Map<String, Object> c = new HashMap<>();
		c.put("name", name);
		c.put("metric", "TREATMENTS");
		c.put("startDate", today.toString());
		c.put("endDate", today.plusDays(9).toString());
		c.put("prize", "Gift card");
		c.put("teams", teamNames);
		c.put("people", List.of(people));
		return c;
	}

	private static Map<String, Object> person(Long userId, Integer team) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("team", team);
		return p;
	}

	private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
		Map<String, Object> copy = new HashMap<>(base);
		copy.put(key, value);
		return copy;
	}

	private long create(Cookie who, Map<String, Object> body) throws Exception {
		String res = mvc.perform(post("/api/contests").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(res).get("id").asLong();
	}

	private void bad(Map<String, Object> body) throws Exception {
		send(manager, post("/api/contests"), body, status().isBadRequest());
	}

	private void send(Cookie who, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
			Map<String, Object> body, ResultMatcher expected) throws Exception {
		mvc.perform(req.cookie(who).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(expected);
	}

	private Long id(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
	}

	private Cookie signUp(String email, String first, String last, Role role, String suName) throws Exception {
		Cookie cookie = mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
		var u = users.findByEmail(email).orElseThrow();
		if (role != null) u.setRoles(role);
		u.setSalonUltimateName(suName);
		users.save(u);
		return cookie;
	}
}
