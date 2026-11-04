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

	/**
	 * Dice that land where a test puts them: {@code roll} for every 1 to 100 roll (0 = everything
	 * goes well), {@code chance} for every other (0 = every rare thing happens, 0.99 = none do).
	 * Scenarios are dealt in list order.
	 */
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
	private Long avaId, miaId, raeId;

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
		raeId = id("rae@grove.test");
		when(salonUltimate.isConfigured()).thenReturn(true);
		sales(Map.of());
		dice.roll = 0;
		dice.chance = 0.99;
		service.setRandom(dice);
		service.setPractice(false);
	}

	@Test
	void everyoneOnStaffSeesGamesButOnlyManagersStartThem() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		mvc.perform(get("/api/trail").cookie(rae))
			.andExpect(jsonPath("$[0].name").value("Grove Trail"))
			.andExpect(jsonPath("$[0].status").value("LIVE"))
			.andExpect(jsonPath("$[0].turn").value(1))
			.andExpect(jsonPath("$[0].parties.length()").value(2))
			.andExpect(jsonPath("$[0].me").doesNotExist());
		mvc.perform(get("/api/trail/" + id).cookie(ava))
			.andExpect(jsonPath("$.me.coins").value(200))
			.andExpect(jsonPath("$.me.party.food").value(5))
			.andExpect(jsonPath("$.me.ready").value(true))
			.andExpect(jsonPath("$.me.turn.options.length()").value(4))
			.andExpect(jsonPath("$.me.store.name").value("Maple Grove, Minnesota Territory"))
			.andExpect(jsonPath("$.landmarks[19].name").value("The Pacific at Haystack Rock"))
			.andExpect(jsonPath("$.landmarks[0].lon").value(-93.46));
		send(ava, post("/api/trail"), game("No", avaId, miaId), status().isForbidden());
		mvc.perform(post("/api/trail/" + id + "/unlock").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/trail/" + id).cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(get("/api/trail")).andExpect(status().isUnauthorized());
		// Receptionists can't be picked, even when linked to Salon Ultimate.
		mvc.perform(get("/api/trail/people").cookie(manager))
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[?(@.name == 'Rae Hill')]").isEmpty());
	}

	@Test
	void aGameStartingLaterShowsTheFirstChoicesButWaitsToStart() throws Exception {
		Map<String, Object> later = game("Later", avaId, miaId);
		later.put("startDate", today.plusDays(3).toString());
		long id = create(later);
		JsonNode g = view(ava, id);
		assertThat(g.get("status").asText()).isEqualTo("UPCOMING");
		assertThat(g.at("/me/ready").asBoolean()).isFalse();
		assertThat(g.at("/me/turn/options").size()).isEqualTo(4);
		send(ava, post("/api/trail/" + id + "/play"), Map.of("option", 0), status().isBadRequest());
	}

	@Test
	void practiceModeLetsYouPlayTurnAfterTurnWithMoreCoins() throws Exception {
		service.setPractice(true);
		long id = create(game("Practice", avaId, miaId));
		JsonNode g = view(ava, id);
		assertThat(g.get("practice").asBoolean()).isTrue();
		assertThat(g.at("/me/coins").asInt()).isEqualTo(2000);
		for (int i = 0; i < 5; i++) go(ava, id, Map.of("option", 0));
		JsonNode after = view(ava, id);
		assertThat(after.at("/me/turnsPlayed").asInt()).isEqualTo(5);
		assertThat(after.at("/me/ready").asBoolean()).isTrue();
		// No limit on turns: keep going as long as you like.
		for (int i = 0; i < 55 && view(ava, id).at("/me/ready").asBoolean(); i++) go(ava, id, Map.of("option", 0));
		assertThat(view(ava, id).at("/me/turnsPlayed").asInt()).isGreaterThan(50);
	}

	@Test
	void goingWithoutFoodGetsWorseUntilYouCantTravelAndNothingNeedsFoodYouDontHave() throws Exception {
		service.setPractice(true);
		long id = create(game("Hungry", avaId, miaId));
		List<String> stages = new java.util.ArrayList<>();
		for (int i = 0; i < 11; i++) {
			JsonNode g = view(ava, id);
			// Every choice offered actually works: no selling or throwing provisions you don't have.
			for (JsonNode o : g.at("/me/turn/options")) assertThat(o.get("available").asBoolean()).as(o.get("label").asText()).isTrue();
			if (g.at("/me/party/food").asInt() == 0) {
				assertThat(g.at("/me/turn/options").findValuesAsText("label")).noneMatch(l -> l.contains("provisions") && l.startsWith("Sell"));
			}
			go(ava, id, Map.of());
			stages.add(view(ava, id).at("/me/party/hunger").asText(""));
		}
		// 5 provisions last 5 turns, then hungry, weak, starving, and finally too weak to travel.
		assertThat(stages).contains("Hungry: half speed");
		assertThat(stages).anyMatch(s -> s.startsWith("Weak with hunger"));
		assertThat(stages).anyMatch(s -> s.startsWith("Starving"));
		assertThat(stages.get(stages.size() - 1)).startsWith("Too weak to travel");
		int miles = party(view(ava, id), avaId).get("miles").asInt();
		go(ava, id, Map.of());
		assertThat(party(view(ava, id), avaId).get("miles").asInt()).isEqualTo(miles);
		assertThat(bodies(view(ava, id))).anyMatch(b -> b.startsWith("Too weak to travel"));
	}

	@Test
	void aGameNeedsAtLeastTwoPlayersAndNoReceptionists() throws Exception {
		send(manager, post("/api/trail"), game("Lonely", avaId), status().isBadRequest());
		send(manager, post("/api/trail"), game("Front desk", avaId, raeId), status().isBadRequest());
		assertThat(games.findAll()).isEmpty();
		create(game("Just right", avaId, miaId));
	}

	@Test
	void coinsComeFromServiceSalesAndBonusesOnlyOnce() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		sales(Map.of("Ava Stone", 480));
		treatments("Ava Stone", 2);
		unlock(id);
		TrailPlayer a = player(id, avaId);
		assertThat(a.getEarned()).isEqualTo(480 + 2 * 25);
		assertThat(a.getCoins()).isEqualTo(200 + 480 + 50);
		assertThat(player(id, miaId).getEarned()).isZero();

		// The next check sees the running total; only what's new is added.
		sales(Map.of("Ava Stone", 600));
		treatments("Ava Stone", 2);
		unlock(id);
		assertThat(player(id, avaId).getEarned()).isEqualTo(600 + 50);
	}

	@Test
	void goPlaysYourTurnRightAwayAndTheNextWaitsForTheNextCheck() throws Exception {
		// A game starting today starts at turn 1.
		long id = create(game("Grove Trail", avaId, miaId));
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/ready").asBoolean()).isTrue();
		assertThat(g.at("/me/turn/kind").asText()).isEqualTo("SCENARIO");
		assertThat(g.at("/me/turn/options").size()).isEqualTo(4);
		assertThat(g.at("/me/turn/options/0").has("good")).isFalse();

		send(ava, post("/api/trail/" + id + "/play"), Map.of("option", 0), status().isOk());
		JsonNode after = view(ava, id);
		assertThat(after.at("/me/turnsPlayed").asInt()).isEqualTo(1);
		assertThat(after.at("/me/ready").asBoolean()).isFalse();
		// Just the result: not the question again, and no turn numbers.
		List<String> lines = new java.util.ArrayList<>();
		after.at("/me/lastResult").forEach(l -> lines.add(l.asText()));
		assertThat(lines).isNotEmpty().noneMatch(l -> l.contains(g.at("/me/turn/text").asText()) || l.contains("picked") || l.contains("turn"));
		// Every turn ends by saying, in real miles, how far you got.
		assertThat(lines).anyMatch(l -> l.matches("(You cover|Slow going: you cover) \\d+ miles?\\.|You don't get anywhere\\.|You lose \\d+ miles? of ground\\."));
		// On foot, a 40 turn game moves about 28 miles a turn.
		assertThat(party(after, avaId).get("miles").asInt()).isPositive();
		// The next turn is already dealt but waits for the next check.
		assertThat(after.at("/me/turn/text").asText()).isNotBlank();
		send(ava, post("/api/trail/" + id + "/play"), Map.of("option", 0), status().isBadRequest());
		// Mia hasn't played; her turn waits for her.
		assertThat(view(mia, id).at("/me/ready").asBoolean()).isTrue();
	}

	@Test
	void everyScenarioHasFourChoicesAndOneSafeOne() {
		assertThat(TrailScenarios.ALL).hasSizeGreaterThanOrEqualTo(40);
		for (TrailScenarios.Scenario sc : TrailScenarios.ALL) {
			assertThat(sc.options()).as(sc.key()).hasSize(4);
			assertThat(sc.options().stream().filter(o -> o.isDefault).count()).as(sc.key()).isEqualTo(1);
			assertThat(sc.options().get(sc.defaultOption()).needs).as(sc.key()).isEqualTo(TrailScenarios.Needs.NOTHING);
			// Only wagon scenarios talk about your wagon: people on foot or horseback get these too.
			if (sc.who() != TrailScenarios.Who.WAGON) {
				for (TrailScenarios.Option o : sc.options()) {
					assertThat(o.label.toLowerCase()).as(sc.key()).doesNotContain("wagon bed", "the wagon", "your wagon", "axle", "oxen");
				}
			}
		}
	}

	@Test
	void distanceIsSaidInRealMiles() {
		assertThat(TrailService.distance(28, 28)).isEqualTo("You cover 28 miles.");
		assertThat(TrailService.distance(21, 28)).isEqualTo("Slow going: you cover 21 miles.");
		assertThat(TrailService.distance(0, 28)).isEqualTo("You don't get anywhere.");
		assertThat(TrailService.distance(-12, 28)).isEqualTo("You lose 12 miles of ground.");
		// Nothing a player reads mentions turns.
		for (TrailScenarios.Scenario sc : TrailScenarios.ALL) {
			for (TrailScenarios.Option o : sc.options()) {
				assertThat(o.good.text + " " + o.bad.text).as(sc.key()).doesNotContainPattern("(?i)\\b(lose|next|a) (a |half a |quarter )?turn\\b").doesNotContain(" miles");
			}
		}
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
		assertThat(TrailOdds.stealChance(1000, 2000, false)).isEqualTo(63);
		assertThat(TrailOdds.stealChance(2000, 1000, false)).isEqualTo(37);
		assertThat(TrailOdds.stealChance(1000, 100000, false)).isEqualTo(70);
		assertThat(TrailOdds.stealChance(2000, 1000, true)).isEqualTo(27);
	}

	@Test
	void storesOnlyOpenInTownsOrWhenAPeddlerTurnsUp() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		// The salon's store is open before leaving.
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "FOOD", "quantity", 2), status().isOk());
		assertThat(view(ava, id).at("/me/party/food").asInt()).isEqualTo(7);
		unlock(id);
		go(ava, id, Map.of());
		// Mile 28 has no trading post, and no trader came by.
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/store").isNull()).isTrue();
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "FOOD", "quantity", 2), status().isBadRequest());

		dice.chance = 0.1;
		unlock(id);
		go(ava, id, Map.of());
		assertThat(view(ava, id).at("/me/store/name").asText()).isEqualTo("A traveling trader");
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "FOOD", "quantity", 2), status().isOk());
	}

	@Test
	void aWagonBreaksDownAndStaysPutUntilRepaired() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		sales(Map.of("Ava Stone", 8000));
		unlock(id);
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "WAGON"), status().isOk());
		dice.chance = 0.0;
		go(ava, id, Map.of());
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/party/wagonBroken").asBoolean()).isTrue();
		int miles = party(g, avaId).get("miles").asInt();
		dice.chance = 0.99;
		unlock(id);
		go(ava, id, Map.of());
		assertThat(party(view(ava, id), avaId).get("miles").asInt()).isEqualTo(miles);
		// A mechanic comes out anywhere, store or not.
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "REPAIR"), status().isOk());
		assertThat(view(ava, id).at("/me/party/wagonBroken").asBoolean()).isFalse();
		unlock(id);
		go(ava, id, Map.of());
		assertThat(party(view(ava, id), avaId).get("miles").asInt()).isGreaterThan(miles);
	}

	@Test
	void peopleOnTheSameStretchSometimesMeetAndCanTravelTogether() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		travelTogether(id);
		JsonNode g = view(ava, id);
		assertThat(g.at("/me/party/members").size()).isEqualTo(2);
		assertThat(g.at("/me/party/food").asInt()).isPositive();
		assertThat(g.get("parties").size()).isEqualTo(1);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Ava Stone is now traveling with Mia Cruz"));

		// Split purchases are logged by who paid.
		dice.chance = 0.1;
		unlock(id);
		go(mia, id, Map.of());
		send(ava, post("/api/trail/" + id + "/buy"), Map.of("item", "FOOD", "quantity", 10, "split", true), status().isOk());
		List<String> paid = view(mia, id).at("/me/purchases").findValuesAsText("body");
		assertThat(paid).contains("Ava Stone paid 60 for 10 x Provisions", "Mia Cruz paid 60 for 10 x Provisions");
	}

	@Test
	void aMeetingCanTurnIntoARobbery() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		sales(Map.of("Ava Stone", 1000));
		meet(id);
		JsonNode meeting = view(mia, id).at("/me/turn");
		assertThat(meeting.get("kind").asText()).isEqualTo("MEETING");
		assertThat(meeting.get("text").asText()).startsWith("You come upon Ava Stone's camp");
		assertThat(meeting.get("options").size()).isEqualTo(4);
		int before = player(id, avaId).getCoins();
		go(mia, id, Map.of("answer", "STEAL_COINS"));
		assertThat(player(id, avaId).getCoins()).isLessThan(before);
		assertThat(bodies(view(ava, id))).anyMatch(b -> b.startsWith("Mia Cruz robbed Ava Stone of"));
		// One meeting, one chance: the next turn is an ordinary scenario.
		assertThat(view(mia, id).at("/me/turn/kind").asText()).isEqualTo("SCENARIO");
	}

	@Test
	void helpingEarnsAGoodSamaritanLabel() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		meet(id);
		go(mia, id, Map.of("answer", "HELP_FOOD"));
		JsonNode g = view(ava, id);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Mia Cruz gave Ava Stone"));
		assertThat(party(g, miaId).toString()).contains("Good Samaritan");
	}

	@Test
	void leavingAPartyTakesAShareOfTheFoodAndNeedsSomeSpace() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		travelTogether(id);
		int food = view(mia, id).at("/me/party/food").asInt();
		send(mia, post("/api/trail/" + id + "/leave"), Map.of(), status().isOk());
		JsonNode g = view(mia, id);
		assertThat(g.at("/me/party/members").size()).isEqualTo(1);
		assertThat(g.at("/me/party/food").asInt()).isEqualTo(food / 2);
		assertThat(g.at("/me/noJoinUntil").asInt()).isEqualTo(3 + 4);
		assertThat(bodies(g)).anyMatch(b -> b.contains("Mia Cruz left their party"));
	}

	@Test
	void aSkimmerWhoGetsAwayWithItCanStillBeGottenEvenWith() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		travelTogether(id);
		JsonNode skim = skimOffer(id);
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
		long id = create(game("Grove Trail", avaId, miaId));
		travelTogether(id);
		JsonNode skim = skimOffer(id);
		dice.roll = 0;
		send(ava, post("/api/trail/" + id + "/choices/" + skim.get("id").asLong()), Map.of("answer", "COINS"), status().isOk());
		JsonNode g = view(mia, id);
		assertThat(bodies(g)).anyMatch(b -> b.startsWith("Ava Stone took") && b.contains("Caught"));
		JsonNode vote = choice(g, "VOTE_OUT");
		send(mia, post("/api/trail/" + id + "/choices/" + vote.get("id").asLong()), Map.of("answer", "YES"), status().isOk());

		JsonNode a = view(ava, id);
		assertThat(a.at("/me/party/members").size()).isEqualTo(1);
		assertThat(a.at("/me/party/food").asInt()).isZero();
		assertThat(a.at("/me/noJoinUntil").asInt()).isEqualTo(6 + 8);
		assertThat(party(a, avaId).toString()).contains("Sticky Fingers");
	}

	@Test
	void theGameRunsUntilAManagerEndsItAndTheStandingsArePosted() throws Exception {
		long id = create(game("Grove Trail", avaId, miaId));
		long postsBefore = posts.count();
		for (int i = 0; i < 60; i++) unlock(id);
		assertThat(view(ava, id).get("status").asText()).isEqualTo("LIVE");
		go(ava, id, Map.of());
		mvc.perform(post("/api/trail/" + id + "/end").cookie(ava)).andExpect(status().isForbidden());
		mvc.perform(post("/api/trail/" + id + "/end").cookie(manager)).andExpect(status().isOk());
		JsonNode g = view(ava, id);
		assertThat(g.get("status").asText()).isEqualTo("FINISHED");
		assertThat(party(g, avaId).get("place").asInt()).isEqualTo(1);
		assertThat(posts.findAll().stream().skip(postsBefore).map(p -> p.getBody()))
			.anyMatch(b -> b.startsWith("Grove Trail is over"));
		mvc.perform(post("/api/trail/" + id + "/unlock").cookie(manager)).andExpect(status().isBadRequest());
	}

	// Helpers.

	/**
	 * Both play turn 1 with the safe choice and end up on the same stretch; Mia, going second,
	 * runs into Ava on her next turn (with the offer to travel together) once turn 2 unlocks.
	 */
	private void meet(long id) throws Exception {
		unlock(id);
		go(ava, id, Map.of());
		dice.chance = 0.0;
		go(mia, id, Map.of());
		dice.chance = 0.99;
		unlock(id);
	}

	/** Mia asks Ava to travel together and Ava says yes, on turn 2. */
	private void travelTogether(long id) throws Exception {
		meet(id);
		assertThat(view(mia, id).at("/me/turn/options").findValuesAsText("value")).contains("JOIN");
		go(mia, id, Map.of("answer", "JOIN"));
		JsonNode invite = choice(view(ava, id), "INVITE");
		assertThat(invite).isNotNull();
		send(ava, post("/api/trail/" + id + "/choices/" + invite.get("id").asLong()), Map.of("answer", "ACCEPT"), status().isOk());
	}

	/** Play the party's turns up to turn 5, where a skim offer comes up for Ava. */
	private JsonNode skimOffer(long id) throws Exception {
		for (int turn = 3; turn <= 5; turn++) {
			unlock(id);
			if (turn == 5) dice.chance = 0.0;
			go(ava, id, Map.of());
		}
		dice.chance = 0.99;
		JsonNode skim = choice(view(ava, id), "SKIM");
		assertThat(skim).isNotNull();
		return skim;
	}

	private void go(Cookie who, long id, Map<String, Object> body) throws Exception {
		send(who, post("/api/trail/" + id + "/play"), body, status().isOk());
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

	private void unlock(long id) throws Exception {
		mvc.perform(post("/api/trail/" + id + "/unlock").cookie(manager)).andExpect(status().isOk());
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

	private Map<String, Object> game(String name, Long... userIds) {
		Map<String, Object> g = new HashMap<>();
		g.put("name", name);
		g.put("startDate", today.toString());
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
