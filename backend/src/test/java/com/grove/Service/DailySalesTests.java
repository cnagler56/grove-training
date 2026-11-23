package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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

import com.grove.Domain.DailySales;
import com.grove.Domain.Role;
import com.grove.Domain.UserNotification;
import com.grove.Repository.DailySalesRepository;
import com.grove.Repository.UserNotificationRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;

/** Each day's business after closing, open-timecard alerts, and the owner's projection. Made-up numbers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailySalesTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired UserNotificationRepository inbox;
	@Autowired DailySalesRepository days;
	@Autowired DailySalesService service;
	@Autowired com.grove.Repository.AppSettingRepository settings;
	@MockBean SalonUltimateClient salonUltimate;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	private Long ownerId;
	private Long managerId;
	private Long stylistId;
	private final LocalDate day = LocalDate.of(2026, 11, 18);

	@BeforeEach
	void setUp() throws Exception {
		days.deleteAll();
		settings.findById(com.grove.Domain.Permission.TIMECARD_ALERTS.key()).ifPresent(settings::delete);
		settings.findById("dailySales.summaryOff").ifPresent(settings::delete);
		inbox.deleteAll();
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		signUp("max@grove.test", "Max", "Hill");
		signUp("ava@grove.test", "Ava", "Stone");
		setRole("max@grove.test", Role.MANAGER);
		ownerId = id("owner@grove.test");
		managerId = id("max@grove.test");
		stylistId = id("ava@grove.test");
		when(salonUltimate.isConfigured()).thenReturn(true);
		when(salonUltimate.weekReport(any(), any())).thenReturn(SalonUltimateReportTest.REPORT);
		when(salonUltimate.stylistComparison(any(), any())).thenReturn(SalonUltimateReportTest.COMPARISON);
	}

	@Test
	void eachDayIsPulledHalfAnHourAfterClosing() {
		assertThat(DailySalesService.pullTime(LocalDate.of(2026, 11, 18))).isEqualTo(LocalDateTime.of(2026, 11, 18, 20, 30)); // Wed
		assertThat(DailySalesService.pullTime(LocalDate.of(2026, 11, 20))).isEqualTo(LocalDateTime.of(2026, 11, 20, 18, 30)); // Fri
		assertThat(DailySalesService.pullTime(LocalDate.of(2026, 11, 21))).isEqualTo(LocalDateTime.of(2026, 11, 21, 17, 30)); // Sat
		assertThat(DailySalesService.pullTime(LocalDate.of(2026, 11, 22))).isEqualTo(LocalDateTime.of(2026, 11, 22, 16, 30)); // Sun
	}

	@Test
	void theDayIsSavedAndOwnersGetASummary() {
		DailySales d = service.pull(day, true);
		assertThat(d.getStatus()).isEqualTo(DailySales.Status.OK);
		assertThat(d.total()).isEqualByComparingTo("1620.50");
		assertThat(d.getRetailSales()).isEqualByComparingTo("80.00");
		assertThat(d.getClients()).isEqualTo(40);
		assertThat(d.getServices()).isEqualTo(56);
		assertThat(d.getStaffWorked()).isEqualTo(2);

		List<UserNotification> sent = inbox.findAll();
		assertThat(sent).filteredOn(n -> n.getUserId().equals(ownerId)).singleElement()
			.satisfies(n -> {
				assertThat(n.getTitle()).startsWith("Today's business");
				assertThat(n.getBody()).contains("$1,621 in sales").contains("40 clients");
			});
		assertThat(sent).noneMatch(n -> n.getUserId().equals(managerId) || n.getUserId().equals(stylistId));

		// Pulling again (catching up) doesn't send it twice.
		service.pull(day, true);
		assertThat(inbox.findAll()).hasSize(1);
	}

	@Test
	void anOpenTimecardAlertsManagersAndUpOnce() throws Exception {
		when(salonUltimate.weekReport(any(), any())).thenReturn(
			"Could Not Display Report. There are unclosed time cards for the following employees: Ava Stone on 11/18/2026. Close them.");
		DailySales d = service.pull(day, true);
		assertThat(d.getStatus()).isEqualTo(DailySales.Status.BLOCKED);
		assertThat(d.getMessage()).contains("Ava Stone on 11/18/2026");

		List<UserNotification> sent = inbox.findAll();
		assertThat(sent).anyMatch(n -> n.getUserId().equals(managerId) && n.getTitle().equals("Open timecard")
			&& n.getBody().contains("Ava Stone"));
		assertThat(sent).anyMatch(n -> n.getUserId().equals(ownerId) && n.getTitle().equals("Open timecard"));
		assertThat(sent).anyMatch(n -> n.getUserId().equals(ownerId) && n.getTitle().contains("waiting on a timecard"));
		assertThat(sent).noneMatch(n -> n.getUserId().equals(stylistId));
		int count = sent.size();

		// No second alert; once it's closed, a quiet pull saves the day with no summary.
		service.pull(day, true);
		assertThat(inbox.findAll()).hasSize(count);
		when(salonUltimate.weekReport(any(), any())).thenReturn(SalonUltimateReportTest.REPORT);
		assertThat(service.pull(day, false).getStatus()).isEqualTo(DailySales.Status.OK);
		assertThat(inbox.findAll()).hasSize(count);
	}

	@Test
	void whoGetsTimecardAlertsFollowsSettings() throws Exception {
		when(salonUltimate.weekReport(any(), any())).thenReturn(
			"Could Not Display Report. There are unclosed time cards for the following employees: Ava Stone on 11/18/2026. Close them.");
		// The owner turns alerts off for managers and on for stylists.
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/owner/permissions").cookie(owner)
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":{\"TIMECARD_ALERTS\":[\"STYLIST\"]}}"))
			.andExpect(status().isOk());
		service.pull(day, true);
		List<UserNotification> alerts = inbox.findAll().stream().filter(n -> n.getTitle().equals("Open timecard")).toList();
		assertThat(alerts).extracting(UserNotification::getUserId).containsExactlyInAnyOrder(ownerId, stylistId);
	}

	@Test
	void eachOwnerCanTurnTheSummaryOff() throws Exception {
		signUp("co@grove.test", "Cora", "Owner");
		setRole("co@grove.test", Role.OWNER);
		Long coId = id("co@grove.test");
		mvc.perform(get("/api/owner/daily-sales/recipients").cookie(owner))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].on").value(true));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/owner/daily-sales/recipients")
				.cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + coId + ",\"on\":false}"))
			.andExpect(jsonPath("$[?(@.name=='Cora Owner')].on").value(false));
		// Only owners can be on the list.
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/owner/daily-sales/recipients")
				.cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + managerId + ",\"on\":true}"))
			.andExpect(status().isBadRequest());

		service.pull(day, true);
		assertThat(inbox.findAll()).filteredOn(n -> n.getTitle().startsWith("Today's business"))
			.extracting(UserNotification::getUserId).containsExactly(ownerId);
	}

	@Test
	void theMonthIsProjectedFromTheAverageOpenDay() {
		YearMonth nov = YearMonth.of(2026, 11);
		List<DailySales> month = List.of(open(1, "1000"), open(2, "2000"), closed(3), open(4, "3000"));
		var p = DailySalesService.projection(month, nov);
		assertThat(p.monthToDate()).isEqualByComparingTo("6000");
		assertThat(p.openDaysSoFar()).isEqualTo(3);
		assertThat(p.averagePerOpenDay()).isEqualByComparingTo("2000");
		// November has 30 days; one was closed.
		assertThat(p.openDaysInMonth()).isEqualTo(29);
		assertThat(p.projected()).isEqualByComparingTo("58000");
		// Nothing yet: nothing projected.
		assertThat(DailySalesService.projection(List.of(), nov).projected()).isEqualByComparingTo("0");
	}

	@Test
	void onlyOwnersSeeTheCard() throws Exception {
		service.pull(LocalDate.now().minusDays(1), false);
		mvc.perform(get("/api/owner/daily-sales").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configured").value(true))
			.andExpect(jsonPath("$.latest.total").value(1620.5))
			.andExpect(jsonPath("$.latest.clients").value(40));
		Cookie manager = login("max@grove.test");
		mvc.perform(get("/api/owner/daily-sales").cookie(manager)).andExpect(status().isForbidden());
	}

	@Test
	void anOwnerCanTryABlockedDayAgainOnceTheTimecardIsClosed() throws Exception {
		LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("America/Chicago")).minusDays(1);
		when(salonUltimate.weekReport(any(), any())).thenReturn(
			"Could Not Display Report. There are unclosed time cards for the following employees: Ava Stone. Close them.");
		assertThat(service.pull(yesterday, true).getStatus()).isEqualTo(DailySales.Status.BLOCKED);
		String label = yesterday.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US));
		// The card's usual catch-up may pull today too, so count only notifications about this day.
		java.util.function.Supplier<Long> aboutIt = () -> inbox.findAll().stream()
			.filter(n -> (n.getTitle() + " " + n.getBody()).contains(label)).count();
		long notified = aboutIt.get();

		// She clocks out; the owner presses Try again.
		when(salonUltimate.weekReport(any(), any())).thenReturn(SalonUltimateReportTest.REPORT);
		mvc.perform(post("/api/owner/daily-sales/" + yesterday + "/retry").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.days[?(@.date == '" + yesterday + "')].status").value("OK"));
		assertThat(days.findById(yesterday).orElseThrow().getStatus()).isEqualTo(DailySales.Status.OK);
		assertThat(aboutIt.get()).isEqualTo(notified); // quiet

		// Only owners, and only recent days that have happened.
		mvc.perform(post("/api/owner/daily-sales/" + yesterday + "/retry").cookie(login("max@grove.test")))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/owner/daily-sales/" + yesterday.plusDays(2) + "/retry").cookie(owner))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/owner/daily-sales/" + yesterday.minusDays(60) + "/retry").cookie(owner))
			.andExpect(status().isBadRequest());
	}

	private static DailySales open(int dayOfMonth, String net) {
		DailySales d = new DailySales(LocalDate.of(2026, 11, dayOfMonth));
		d.setStatus(DailySales.Status.OK);
		d.setStaffWorked(3);
		d.setNetServiceSales(new BigDecimal(net));
		d.setRetailSales(BigDecimal.ZERO);
		return d;
	}

	private static DailySales closed(int dayOfMonth) {
		DailySales d = new DailySales(LocalDate.of(2026, 11, dayOfMonth));
		d.setStatus(DailySales.Status.OK);
		d.setStaffWorked(0);
		return d;
	}

	private Long id(String email) {
		return users.findByEmail(email).orElseThrow().getUserId();
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
