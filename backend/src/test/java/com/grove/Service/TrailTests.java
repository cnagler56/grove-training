package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grove.Domain.Role;
import com.grove.Domain.TrailPlayer;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.TrailChoiceRepository;
import com.grove.Repository.TrailGameRepository;
import com.grove.Repository.TrailLogRepository;
import com.grove.Repository.TrailPartyRepository;
import com.grove.Repository.TrailPlayerRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrailTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired TrailGameRepository games;
	@Autowired TrailPartyRepository parties;
	@Autowired TrailPlayerRepository players;
	@Autowired TrailChoiceRepository choices;
	@Autowired TrailLogRepository logs;
	@Autowired TeamPostRepository posts;
	@Autowired TrailService service;
	@MockBean SalonUltimateClient salonUltimate;
	@MockBean JavaMailSender mailSender;

	/** Dice that land where a test puts them: {@code roll} for every 1 to 100 roll, {@code chance} for every other. */
	static class Dice extends Random {
		int roll = 0;
		double chance = 0.99;

		@Override
		public int nextInt(int bound) {
			return bound == 100 ? roll : 0;
		}

		@Override
		public double nextDouble() {
			return chance;
		}
	}

	private final LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
	private final Dice dice = new Dice();
	private Cookie manager, ava, mia, rae;
	private Long avaId, miaId, leeId, raeId;

	@BeforeEach
	void setUp() throws Exception {
		logs.deleteAll();
		choices.deleteAll();
		players.deleteAll();
		parties.deleteAll();
		games.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		signUp("owner@grove.test", "Owner", "Person", null, null);
		manager = signUp("manny@grove.test", "Manny", "Reed", Role.MANAGER, null);
		ava = signUp("ava@grove.test", "Ava", "Stone", null, "Ava Stone");
		mia = signUp("mia@grove.test", "Mia", "Cruz", null, "Mia Cruz");
		signUp("lee@grove.test", "Lee", "Moss", null, "Lee Moss");
		rae = signUp("rae@grove.test", "Rae", "Hill", Role.RECEPTIONIST, "Rae Hill");
		avaId = id("ava@grove.test");
		miaId = id("mia@grove.test");
		leeId = id("lee@grove.test");
		raeId = id("rae@grove.test");
		when(salonUltimate.isConfigured()).thenReturn(true);
		sales(Map.of());
		dice.roll = 0;
		dice.chance = 0.99;
		service.setRandom(dice);
	}

	@Test
	void everyoneOnStaffSeesGamesButOnlyManagersStartThem() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		mvc.perform(get("/api/trail").cookie(rae))
			.andExpect(jsonPath("$[0].name").value("Grove Trail"))
			.andExpect(jsonPath("$[0].status").value("UPCOMING"))
			.andExpect(jsonPath("$[0].parties.length()").value(2))
			.andExpect(jsonPath("$[0].me").doesNotExist());
		mvc.perform(get("/api/trail/" + id).cookie(ava))
			.andExpect(jsonPath("$.me.coins").value(200))
			.andExpect(jsonPath("$.me.mount").value("WALK"))
			.andExpect(jsonPath("$.me.party.food").value(3))
			.andExpect(jsonPath("$.landmarks[0].name").value("Maple Grove, MN"))
			.andExpect(jsonPath("$.trailMiles").value(1870));
		send(ava, post("/api/trail"), game("No", 40, avaId, miaId), status().isForbidden());
		mvc.perform(post("/api/trail/" + id + "/turn").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/trail/" + id).cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(get("/api/trail")).andExpect(status().isUnauthorized());
		// Receptionists can't be picked, even when linked to Salon Ultimate.
		mvc.perform(get("/api/trail/people").cookie(manager))
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[?(@.name == 'Rae Hill')]").isEmpty());
	}

	@Test
	void gamesLastThirtyToFiftyTurnsWithAtLeastTwoPlayers() throws Exception {
		send(manager, post("/api/trail"), game("Short", 29, avaId, miaId), status().isBadRequest());
		send(manager, post("/api/trail"), game("Long", 51, avaId, miaId), status().isBadRequest());
		send(manager, post("/api/trail"), game("Lonely", 40, avaId), status().isBadRequest());
		send(manager, post("/api/trail"), game("Front desk", 40, avaId, raeId), status().isBadRequest());
		assertThat(games.findAll()).isEmpty();
		create(game("Just right", 30, avaId, miaId));
		create(game("Also right", 50, avaId, miaId));
	}

	@Test
	void coinsComeFromServiceSalesAndBonusesOnlyOnce() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		sales(Map.of("Ava Stone", 480));
		treatments("Ava Stone", 2);
		turn(id);
		TrailPlayer a = player(id, avaId);
		assertThat(a.getEarned()).isEqualTo(480 + 2 * 25);
		assertThat(a.getCoins()).isEqualTo(200 + 480 + 50);
		assertThat(player(id, miaId).getEarned()).isZero();

		// The next check sees the running total; only what's new is added.
		sales(Map.of("Ava Stone", 600));
		treatments("Ava Stone", 2);
		turn(id);
		assertThat(player(id, avaId).getEarned()).isEqualTo(600 + 50);
	}

	@Test
	void everyTurnDealsAScenarioAndNobodyAnsweringMeansTheDefault() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		turn(id);
		JsonNode g = view(ava, id);
		assertThat(g.get("turn").asInt()).isEqualTo(1);
		assertThat(g.get("status").asText()).isEqualTo("LIVE");
		// On foot, a 40 turn game moves 28 miles a turn.
		assertThat(g.at("/me/party/food").asInt()).isEqualTo(2);
		assertThat(g.get("parties").get(0).get("miles").asInt()).isEqualTo(28);
		JsonNode scenario = g.at("/me/scenario");
		assertThat(scenario.get("options").size()).isBetween(2, 4);

		int pick = 0;
		send(ava, post("/api/trail/" + id + "/choose"), Map.of("option", pick), status().isOk());
		JsonNode after = view(ava, id);
		assertThat(after.at("/me/scenario/chosen").asInt()).isEqualTo(pick);
		assertThat(after.at("/me/scenario/chosenByName").asText()).isEqualTo("Ava Stone");
		turn(id);
		List<String> avaLog = bodies(view(ava, id));
		assertThat(avaLog).anyMatch(b -> b.contains("Ava Stone picked:"));
		// Mia never answered, so her party got the default.
		assertThat(bodies(view(mia, id))).anyMatch(b -> b.contains("Nobody answered, so:"));
	}

	@Test
	void theRubberBandHelpsWhoeverIsBehind() {
		assertThat(TrailOdds.chance(60, 0, 0)).isEqualTo(60);
		assertThat(TrailOdds.chance(60, 0.1, 0)).isEqualTo(75);
		assertThat(TrailOdds.chance(60, -0.1, 0)).isEqualTo(45);
		// Never more than 20 points from the base, never past 10 to 90.
		assertThat(TrailOdds.chance(60, 0.5, 10)).isEqualTo(80);
		assertThat(TrailOdds.chance(85, 0.5, 0)).isEqualTo(90);
		assertThat(TrailOdds.chance(20, -0.5, -15)).isEqualTo(10);
		// Stealing is easier from someone better off, harder from someone worse off.
		assertThat(TrailOdds.stealChance(1000, 2000, false, false)).isEqualTo(63);
		assertThat(TrailOdds.stealChance(2000, 1000, false, false)).isEqualTo(37);
		assertThat(TrailOdds.stealChance(1000, 100000, false, false)).isEqualTo(70);
		assertThat(TrailOdds.stealChance(2000, 1000, true, false)).isEqualTo(52);
	}

	@Test
	void partiesPoolPurchasesAndLogWhoPaid() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId, leeId));
		send(ava, post("/api/trail/" + id + "/invite"), Map.of("userId", miaId), status().isOk());
		JsonNode invite = view(mia, id).at("/me/choices/0");
		assertThat(invite.get("kind").asText()).isEqualTo("INVITE");
		send(mia, post("/api/trail/" + id + "/choices/" + invite.get("id").asLong()), Map.of("answer", "ACCEPT"), status().isOk());
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/party/members").size()).isEqualTo(2);
		assertThat(g.at("/me/party/food").asInt()).isEqualTo(6);

		// 12 snack packs at 12 each, split: 72 each.
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "FOOD", "quantity", 12, "split", true), status().isOk());
		assertThat(player(id, avaId).getCoins()).isEqualTo(128);
		assertThat(player(id, miaId).getCoins()).isEqualTo(128);
		List<String> paid = view(mia, id).at("/me/purchases").findValuesAsText("body");
		assertThat(paid).contains("Ava Stone paid 72 for 12 x Snack pack", "Mia Cruz paid 72 for 12 x Snack pack");

		// Not enough coins for a horse, alone or together.
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "HORSE", "split", false), status().isBadRequest());
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "WAGON", "split", true), status().isBadRequest());
		// Lee isn't in the party and pays nothing.
		assertThat(player(id, leeId).getCoins()).isEqualTo(200);
	}

	@Test
	void aWagonBreaksDownAndStaysPutUntilRepaired() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		sales(Map.of("Ava Stone", 8000));
		turn(id);
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "WAGON"), status().isOk());
		dice.chance = 0.0;
		turn(id);
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/party/wagonBroken").asBoolean()).isTrue();
		int miles = party(g, avaId).get("miles").asInt();
		turn(id);
		assertThat(party(view(ava, id), avaId).get("miles").asInt()).isEqualTo(miles);
		dice.chance = 0.99;
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "REPAIR"), status().isOk());
		assertThat(view(ava, id).at("/me/party/wagonBroken").asBoolean()).isFalse();
		turn(id);
		assertThat(party(view(ava, id), avaId).get("miles").asInt()).isGreaterThan(miles);
	}

	@Test
	void leavingAPartyTakesAShareOfTheFoodAndNeedsSomeSpace() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId, leeId));
		join(id, ava, mia, miaId);
		send(mia, post("/api/trail/" + id + "/leave"), Map.of(), status().isOk());
		JsonNode g = view(mia, id);
		assertThat(g.at("/me/party/members").size()).isEqualTo(1);
		assertThat(g.at("/me/party/food").asInt()).isEqualTo(3);
		assertThat(g.at("/me/noJoinUntil").asInt()).isEqualTo(4);
		assertThat(view(ava, id).at("/me/party/food").asInt()).isEqualTo(3);
		// Nobody can invite her back yet.
		send(ava, post("/api/trail/" + id + "/invite"), Map.of("userId", miaId), status().isBadRequest());
		assertThat(bodies(view(ava, id))).anyMatch(b -> b.contains("Mia Cruz left their party"));
	}

	@Test
	void aSkimmerWhoGetsAwayWithItCanStillBeGottenEvenWith() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		join(id, ava, mia, miaId);
		for (int i = 0; i < 4; i++) turn(id);
		dice.chance = 0.0;
		turn(id);
		JsonNode skim = choice(view(ava, id), "SKIM");
		assertThat(skim).isNotNull();
		int miaBefore = player(id, miaId).getCoins();
		dice.roll = 99;
		send(ava, post("/api/trail/" + id + "/choices/" + skim.get("id").asLong()), Map.of("answer", "COINS"), status().isOk());
		int taken = miaBefore - player(id, miaId).getCoins();
		assertThat(taken).isPositive();
		JsonNode g = view(mia, id);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Someone took " + taken + " coins from the party"));
		assertThat(party(g, avaId).toString()).doesNotContain("Sticky Fingers");

		JsonNode even = choice(g, "GET_EVEN");
		dice.roll = 0;
		send(mia, post("/api/trail/" + id + "/choices/" + even.get("id").asLong()), Map.of("answer", "YES"), status().isOk());
		assertThat(player(id, miaId).getCoins()).isEqualTo(miaBefore + taken);
		assertThat(bodies(view(mia, id))).anyMatch(b -> b.startsWith("Mia Cruz got even with Ava Stone"));
	}

	@Test
	void aCaughtSkimmerGetsStickyFingersAndCanBeVotedOut() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		join(id, ava, mia, miaId);
		for (int i = 0; i < 4; i++) turn(id);
		dice.chance = 0.0;
		turn(id);
		JsonNode skim = choice(view(ava, id), "SKIM");
		dice.roll = 0;
		send(ava, post("/api/trail/" + id + "/choices/" + skim.get("id").asLong()), Map.of("answer", "COINS"), status().isOk());
		JsonNode g = view(mia, id);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Ava Stone took") && b.contains("Caught"));
		JsonNode vote = choice(g, "VOTE_OUT");
		send(mia, post("/api/trail/" + id + "/choices/" + vote.get("id").asLong()), Map.of("answer", "YES"), status().isOk());

		JsonNode a = view(ava, id);
		assertThat(a.at("/me/party/members").size()).isEqualTo(1);
		assertThat(a.at("/me/party/food").asInt()).isZero();
		assertThat(a.at("/me/noJoinUntil").asInt()).isEqualTo(5 + 8);
		assertThat(party(a, avaId).toString()).contains("Sticky Fingers");
	}

	@Test
	void peopleWhoMeetCanRobOrHelpEachOther() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		sales(Map.of("Mia Cruz", 1000));
		turn(id);
		JsonNode avaMeet = choice(view(ava, id), "MEET");
		JsonNode miaMeet = choice(view(mia, id), "MEET");
		assertThat(avaMeet.get("title").asText()).isEqualTo("You ran into Mia Cruz");
		send(ava, post("/api/trail/" + id + "/choices/" + avaMeet.get("id").asLong()), Map.of("answer", "STEAL_COINS"), status().isOk());
		send(mia, post("/api/trail/" + id + "/choices/" + miaMeet.get("id").asLong()), Map.of("answer", "HELP_FOOD"), status().isOk());
		turn(id);
		JsonNode g = view(mia, id);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Ava Stone robbed Mia Cruz of"));
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Mia Cruz gave Ava Stone"));
		assertThat(party(g, miaId).toString()).contains("Good Samaritan");
	}

	@Test
	void whenBothStealNothingChangesHandsAndTheyNeverMeetAgain() throws Exception {
		long id = create(game("Grove Trail", 40, avaId, miaId));
		turn(id);
		send(ava, post("/api/trail/" + id + "/choices/" + choice(view(ava, id), "MEET").get("id").asLong()),
			Map.of("answer", "STEAL_FOOD"), status().isOk());
		send(mia, post("/api/trail/" + id + "/choices/" + choice(view(mia, id), "MEET").get("id").asLong()),
			Map.of("answer", "STEAL_COINS"), status().isOk());
		turn(id);
		JsonNode g = view(ava, id);
		assertThat(bodies(g)).anyMatch(b -> b.contains("both reached for each other's Glazers"));
		assertThat(party(g, avaId).toString()).contains("Sticky Fingers");
		assertThat(party(g, miaId).toString()).contains("Sticky Fingers");
		for (int i = 0; i < 8; i++) turn(id);
		assertThat(choice(view(ava, id), "MEET")).isNull();
	}

	@Test
	void theGameEndsAfterItsLastTurnAndPostsTheStandings() throws Exception {
		long id = create(game("Grove Trail", 30, avaId, miaId));
		long postsBefore = posts.count();
		for (int i = 0; i < 30; i++) turn(id);
		JsonNode g = view(ava, id);
		assertThat(g.get("status").asText()).isEqualTo("FINISHED");
		assertThat(g.get("parties").get(0).get("place").asInt()).isEqualTo(1);
		assertThat(posts.findAll().stream().skip(postsBefore).map(p -> p.getBody()))
			.anyMatch(b -> b.startsWith("Grove Trail is over after 30 turns"));
		mvc.perform(post("/api/trail/" + id + "/turn").cookie(manager)).andExpect(status().isBadRequest());
	}

	// Helpers.

	private void join(long id, Cookie inviter, Cookie invitee, Long inviteeId) throws Exception {
		send(inviter, post("/api/trail/" + id + "/invite"), Map.of("userId", inviteeId), status().isOk());
		JsonNode invite = choice(view(invitee, id), "INVITE");
		send(invitee, post("/api/trail/" + id + "/choices/" + invite.get("id").asLong()), Map.of("answer", "ACCEPT"), status().isOk());
	}

	private static JsonNode choice(JsonNode game, String kind) {
		for (JsonNode c : game.at("/me/choices")) if (c.get("kind").asText().equals(kind)) return c;
		return null;
	}

	private static JsonNode party(JsonNode game, Long userId) {
		for (JsonNode p : game.get("parties")) {
			for (JsonNode m : p.get("members")) if (m.get("userId").asLong() == userId) return p;
		}
		throw new AssertionError("No party for " + userId);
	}

	private static List<String> bodies(JsonNode game) {
		return game.get("log").findValuesAsText("body");
	}

	private TrailPlayer player(long gameId, Long userId) {
		return players.findByGameIdAndUserId(gameId, userId).orElseThrow();
	}

	private void turn(long id) throws Exception {
		mvc.perform(post("/api/trail/" + id + "/turn").cookie(manager)).andExpect(status().isOk());
	}

	private JsonNode view(Cookie who, long id) throws Exception {
		return json.readTree(mvc.perform(get("/api/trail/" + id).cookie(who)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
	}

	/** Salon Ultimate's Calculate Gross Pay report with each name's discounted service sales. */
	private void sales(Map<String, Integer> byName) throws Exception {
		StringBuilder html = new StringBuilder("<table><tr><td>Method</td></tr>");
		int payrollId = 100;
		for (Map.Entry<String, Integer> e : byName.entrySet()) {
			html.append("<tr><td>").append(payrollId++).append(' ').append(e.getKey()).append("</td><td>30</td><td>0</td><td>0</td><td>0</td>")
				.append("<td>0</td><td>0</td><td>").append(e.getValue()).append("</td><td>").append(e.getValue()).append("</td></tr>");
		}
		when(salonUltimate.weekReport(any(), any())).thenReturn(html.append("</table>").toString());
		when(salonUltimate.detailedServiceByEmployee(any(), any())).thenReturn("<table></table>");
	}

	/** The Detailed Service report with this many treatments for one person. */
	private void treatments(String name, int count) throws Exception {
		String html = "<table><tr><td>Service Provider: " + name + "</td></tr>"
			+ "<tr><td>Category</td><td>Service Code</td><td>Service Description</td><td>Total Units</td><td>Total Dollars</td></tr>"
			+ "<tr><td>Treatment</td></tr><tr><td></td><td>EMT</td><td>Oil Treatment</td><td>" + count + "</td><td>0</td></tr></table>";
		when(salonUltimate.detailedServiceByEmployee(any(), any())).thenReturn(html);
	}

	private Map<String, Object> game(String name, int turns, Long... userIds) {
		Map<String, Object> g = new HashMap<>();
		g.put("name", name);
		g.put("startDate", today.toString());
		g.put("turns", turns);
		g.put("prize", "Gift card");
		g.put("userIds", List.of(userIds));
		return g;
	}

	private long create(Map<String, Object> body) throws Exception {
		String res = mvc.perform(post("/api/trail").cookie(manager).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(res).get("id").asLong();
	}

	private void send(Cookie who, MockHttpServletRequestBuilder req, Map<String, Object> body, ResultMatcher expected) throws Exception {
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
