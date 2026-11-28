package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
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

import com.grove.Domain.AppSetting;
import com.grove.Domain.NotificationKind;
import com.grove.Domain.PushSubscription;
import com.grove.Domain.Role;
import com.grove.Domain.UserNotification;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.PushSubscriptionRepository;
import com.grove.Repository.TeamPostCommentRepository;
import com.grove.Repository.TeamPostImageRepository;
import com.grove.Repository.TeamPostLikeRepository;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.TimeOffRequestRepository;
import com.grove.Repository.UserNotificationRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.VacationPayRequestRepository;
import com.grove.Service.ScheduleService;
import com.grove.Service.SessionService;
import com.grove.Service.VacationPayService;
import com.sun.net.httpserver.HttpServer;

/**
 * Each person picks which kinds of notifications they get on My account. Phones are a
 * stand-in push server; the keys are RFC 8291's published example keys, not real ones.
 */
@SpringBootTest(properties = {
	"app.push.public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
	"app.push.private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
	"app.push.subject=mailto:test@grove.test",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationChoiceTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired UserNotificationRepository inbox;
	@Autowired PushSubscriptionRepository subscriptions;
	@Autowired TimeOffRequestRepository timeOff;
	@Autowired VacationPayRequestRepository vacationPay;
	@Autowired TeamPostRepository posts;
	@Autowired TeamPostImageRepository images;
	@Autowired TeamPostCommentRepository comments;
	@Autowired TeamPostLikeRepository likes;
	@Autowired AppSettingRepository settings;
	@Autowired VacationPayService vacationPayService;
	@MockBean JavaMailSender mailSender;

	private HttpServer server;
	/** How many notifications each phone got, by its address. */
	private final Map<String, AtomicInteger> received = new ConcurrentHashMap<>();
	private Cookie owner;
	private Cookie gm;
	private Cookie ava;
	private Long ownerId;
	private Long gmId;
	private Long avaId;

	@BeforeEach
	void setUp() throws Exception {
		clear();
		settings.deleteAll();
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			received.computeIfAbsent(exchange.getRequestURI().getPath(), p -> new AtomicInteger()).incrementAndGet();
			exchange.sendResponseHeaders(201, -1);
			exchange.close();
		});
		server.start();
		owner = signUp("owner@grove.test", "Owner", "Person");
		gm = signUp("gia@grove.test", "Gia", "Ray");
		ava = signUp("ava@grove.test", "Ava", "Stone");
		setRole("gia@grove.test", Role.GENERAL_MANAGER);
		ownerId = id("owner@grove.test");
		gmId = id("gia@grove.test");
		avaId = id("ava@grove.test");
		device(ownerId, "/owner");
		device(gmId, "/gia");
		device(avaId, "/ava");
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
		clear();
	}

	@Test
	void turningOffTimeOffAndPaidTimeOffStopsThemForThatPersonOnly() throws Exception {
		choose(owner, NotificationKind.TIME_OFF_REQUESTS, false)
			.andExpect(jsonPath("$[?(@.kind=='TIME_OFF_REQUESTS')].on").value(false))
			.andExpect(jsonPath("$[?(@.kind=='TEAM_BOARD')].on").value(true));
		choose(owner, NotificationKind.PTO_REQUESTS, false)
			.andExpect(jsonPath("$[?(@.kind=='PTO_REQUESTS')].on").value(false));

		// Ava asks for time off (owner and general manager approve it) and paid time off (owners only).
		LocalDate day = ScheduleService.weekStart(LocalDate.now().plusDays(30));
		mvc.perform(post("/api/time-off").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + day + "\",\"endDate\":\"" + day + "\"}"))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/vacation-pay").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"hours\":8,\"checkDate\":\"" + vacationPayService.checkDates().get(0) + "\"}"))
			.andExpect(status().isCreated());

		// The general manager still hears about time off; the owner's phone stays quiet.
		await().atMost(Duration.ofSeconds(10)).until(() -> count("/gia") == 1);
		await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> count("/owner") == 0);
		assertThat(titles(gmId)).containsExactly("Time off request");
		assertThat(titles(ownerId)).isEmpty();

		// Other kinds still reach the owner.
		mvc.perform(post("/api/team-board").cookie(gm).contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"Staff meeting Monday\"}"))
			.andExpect(status().isCreated());
		await().atMost(Duration.ofSeconds(10)).until(() -> count("/owner") == 1 && count("/ava") == 1);
		assertThat(titles(ownerId)).containsExactly("Gia Ray posted on the team board");
	}

	@Test
	void turningOneBackOnStartsItAgain() throws Exception {
		choose(gm, NotificationKind.TIME_OFF_REQUESTS, false);
		choose(gm, NotificationKind.TIME_OFF_REQUESTS, true)
			.andExpect(jsonPath("$[?(@.kind=='TIME_OFF_REQUESTS')].on").value(true));
		assertThat(settings.findById(NotificationKind.TIME_OFF_REQUESTS.settingKey()).orElseThrow().getValue()).isEmpty();

		LocalDate day = ScheduleService.weekStart(LocalDate.now().plusDays(30));
		mvc.perform(post("/api/time-off").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + day + "\",\"endDate\":\"" + day + "\"}"))
			.andExpect(status().isCreated());
		assertThat(titles(gmId)).containsExactly("Time off request");
		assertThat(titles(ownerId)).containsExactly("Time off request");
		await().atMost(Duration.ofSeconds(10)).until(() -> count("/gia") == 1 && count("/owner") == 1);
	}

	@Test
	void myAccountListsOnlyWhatCanReachYouAndEverythingStartsOn() throws Exception {
		mvc.perform(get("/api/notifications/choices").cookie(ava))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].kind").value(org.hamcrest.Matchers.contains(
				"MY_REQUESTS", "SCHEDULE", "VIDEOS", "CONTESTS", "TEAM_BOARD")))
			.andExpect(jsonPath("$[*].on").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
			.andExpect(jsonPath("$[0].label").value("Answers to your requests"));
		// Owners can't ask for time off themselves, so they don't get answers.
		mvc.perform(get("/api/notifications/choices").cookie(owner))
			.andExpect(jsonPath("$[*].kind").value(org.hamcrest.Matchers.contains(
				"TIME_OFF_REQUESTS", "PTO_REQUESTS", "SCHEDULE", "PAYROLL_NOTES", "VIDEOS", "CONTESTS", "TEAM_BOARD",
				"OPEN_TIMECARDS", "DAILY_SALES", "JOB_APPLICATIONS")));
		mvc.perform(get("/api/notifications/choices")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/notifications/choices").cookie(ava).contentType(MediaType.APPLICATION_JSON)
				.content("{\"on\":false}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theDailySalesSummaryIsOneSwitchOnMyAccountAndSettings() throws Exception {
		// A choice made on Settings before My account had these still counts.
		settings.save(new AppSetting("dailySales.summaryOff", String.valueOf(ownerId)));
		mvc.perform(get("/api/notifications/choices").cookie(owner))
			.andExpect(jsonPath("$[?(@.kind=='DAILY_SALES')].on").value(false));

		choose(owner, NotificationKind.DAILY_SALES, true);
		mvc.perform(get("/api/owner/daily-sales/recipients").cookie(owner))
			.andExpect(jsonPath("$[0].on").value(true));

		mvc.perform(put("/api/owner/daily-sales/recipients").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + ownerId + ",\"on\":false}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/notifications/choices").cookie(owner))
			.andExpect(jsonPath("$[?(@.kind=='DAILY_SALES')].on").value(false));
	}

	private org.springframework.test.web.servlet.ResultActions choose(Cookie who, NotificationKind kind, boolean on) throws Exception {
		return mvc.perform(put("/api/notifications/choices").cookie(who).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"" + kind + "\",\"on\":" + on + "}"))
			.andExpect(status().isOk());
	}

	private int count(String path) {
		AtomicInteger n = received.get(path);
		return n == null ? 0 : n.get();
	}

	private List<String> titles(Long userId) {
		return inbox.findAll().stream().filter(n -> n.getUserId().equals(userId)).map(UserNotification::getTitle).toList();
	}

	private void clear() {
		inbox.deleteAll();
		subscriptions.deleteAll();
		timeOff.deleteAll();
		vacationPay.deleteAll();
		images.deleteAll();
		comments.deleteAll();
		likes.deleteAll();
		posts.deleteAll();
		for (NotificationKind k : NotificationKind.values()) settings.findById(k.settingKey()).ifPresent(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
	}

	private void device(Long userId, String path) {
		PushSubscription s = new PushSubscription();
		s.setUserId(userId);
		s.setEndpoint("http://localhost:" + server.getAddress().getPort() + path);
		s.setP256dh("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
		s.setAuth("BTBZMqHH6r4Tts7J_aSIgg");
		s.setCreatedAt(LocalDateTime.now());
		subscriptions.save(s);
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
