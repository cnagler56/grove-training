package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import com.grove.Domain.ClosedDay;
import com.grove.Domain.DailySales;
import com.grove.Domain.Holiday;
import com.grove.Domain.Role;
import com.grove.Domain.Shift;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.ClosedDayRepository;
import com.grove.Repository.DailySalesRepository;
import com.grove.Repository.ShiftRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;

/** Monthly goals and what each remaining day needs. Made-up numbers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalsTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired DailySalesRepository days;
	@Autowired ShiftRepository shifts;
	@Autowired AppSettingRepository settings;
	@Autowired GoalsService goals;
	@Autowired ClosedDayService closedDays;
	@Autowired ClosedDayRepository closedDayRepo;
	@MockBean SalonUltimateClient salonUltimate;
	@MockBean JavaMailSender mailSender;

	private final YearMonth nov = YearMonth.of(2026, 11);
	/** Friday. Nov 1-19 are in; Nov 20-30 (11 days) are left. */
	private final LocalDate today = LocalDate.of(2026, 11, 20);
	private Cookie owner;
	private Long stylistId;

	@BeforeEach
	void setUp() throws Exception {
		days.deleteAll();
		shifts.deleteAll();
		closedDayRepo.deleteAll();
		settings.findAll().stream().filter(s -> s.getKey().startsWith("goals.") || s.getKey().startsWith("permission.")
				|| s.getKey().equals(ClosedDayService.HOLIDAYS_KEY))
			.forEach(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		signUp("ava@grove.test", "Ava", "Stone");
		signUp("max@grove.test", "Max", "Hill");
		setRole("max@grove.test", Role.MANAGER);
		stylistId = users.findByEmail("ava@grove.test").orElseThrow().getUserId();

		// History: Saturdays 60 worked hours, Sundays 20, weekdays 40; 2 guests an hour.
		for (LocalDate d = today.minusDays(GoalsService.HISTORY_DAYS); d.isBefore(today); d = d.plusDays(1)) {
			int hours = d.getDayOfWeek() == DayOfWeek.SATURDAY ? 60 : d.getDayOfWeek() == DayOfWeek.SUNDAY ? 20 : 40;
			// This month so far: $1,000 a day, $100 of it retail.
			days.save(day(d, hours, d.getMonthValue() == 11 ? "900" : "500", "100"));
		}
		// Waxes and treatments come from one Detailed Service report for the month so far (4 waxes, 6 treatments).
		org.mockito.Mockito.when(salonUltimate.isConfigured()).thenReturn(true);
		org.mockito.Mockito.when(salonUltimate.detailedServiceByEmployee(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 19)))
			.thenReturn(SalonUltimateReportTest.DETAILED_SERVICE);
		goals.set(nov, new GoalsService.Goals(new BigDecimal("30000"), 60, 80, new BigDecimal("2.00")));
		goals.refreshCounts(nov, today);
	}

	@Test
	void whatsLeftIsSplitByUsualHoursSoSaturdaysCarryMore() {
		GoalsService.GoalsView v = goals.view(nov, today);
		assertThat(v.hasGoals()).isTrue();
		assertThat(v.daysLeft()).isEqualTo(11);
		assertThat(v.days()).hasSize(11);

		GoalsService.GoalLine sales = line(v, "SALES");
		assertThat(sales.done()).isEqualByComparingTo("19000"); // 19 days × $1,000
		assertThat(sales.left()).isEqualByComparingTo("11000");
		assertThat(sales.perDay()).isEqualByComparingTo("1000"); // 11,000 ÷ 11 open days

		GoalsService.DayNeed sat = need(v, LocalDate.of(2026, 11, 21));
		GoalsService.DayNeed sun = need(v, LocalDate.of(2026, 11, 22));
		GoalsService.DayNeed mon = need(v, LocalDate.of(2026, 11, 23));
		assertThat(sat.scheduled()).isFalse();
		assertThat(sat.sales()).isGreaterThan(mon.sales());
		assertThat(mon.sales()).isGreaterThan(sun.sales());
		// 11 days: 2 Saturdays (60), 2 Sundays (20), 7 weekdays (40) = 440 hours. Saturday: 11,000 × 60/440.
		assertThat(sat.sales()).isEqualByComparingTo("1500");
		assertThat(sun.sales()).isEqualByComparingTo("500");

		// Counts are whole and add up to exactly what's left.
		assertThat(v.countsThrough()).isEqualTo(LocalDate.of(2026, 11, 19));
		assertThat(line(v, "WAXES").done()).isEqualByComparingTo("4");
		assertThat(line(v, "WAXES").left()).isEqualByComparingTo("56");
		assertThat(v.days().stream().mapToInt(GoalsService.DayNeed::waxes).sum()).isEqualTo(56);
		assertThat(v.days().stream().mapToInt(GoalsService.DayNeed::treatments).sum()).isEqualTo(80 - 6);
	}

	@Test
	void productPerGuestSaysWhatTheRestOfTheMonthNeeds() {
		GoalsService.GoalsView v = goals.view(nov, today);
		GoalsService.GoalLine ppg = line(v, "PPG");
		// So far: $1,900 retail over 19 days of guests.
		assertThat(ppg.done()).isNotNull();
		assertThat(ppg.perDay()).isNotNull();
		// Behind the goal so far, so the rest of the month needs more than the goal per guest.
		if (ppg.done().compareTo(ppg.goal()) < 0) assertThat(ppg.perDay()).isGreaterThan(ppg.goal());
	}

	@Test
	void aScheduledWeekUsesItsScheduledHoursAndEmptyDaysCountAsClosed() {
		// Week of Sat Nov 21 - Fri Nov 27 is built: only Saturday (10 hours) and Sunday (5 hours).
		shifts.save(shift(LocalDate.of(2026, 11, 21), 8, 18));
		shifts.save(shift(LocalDate.of(2026, 11, 22), 10, 15));
		GoalsService.GoalsView v = goals.view(nov, today);
		GoalsService.DayNeed sat = need(v, LocalDate.of(2026, 11, 21));
		GoalsService.DayNeed tue = need(v, LocalDate.of(2026, 11, 24));
		GoalsService.DayNeed sat2 = need(v, LocalDate.of(2026, 11, 28));
		assertThat(sat.scheduled()).isTrue();
		assertThat(sat.hours()).isEqualByComparingTo("10.0");
		assertThat(tue.scheduled()).isTrue();
		assertThat(tue.hours()).isEqualByComparingTo("0.0");
		assertThat(tue.sales()).isEqualByComparingTo("0");
		assertThat(sat2.scheduled()).isFalse(); // next week isn't built: usual hours
		assertThat(v.daysLeft()).isEqualTo(6); // Nov 20, 21, 22, 28, 29, 30
	}

	@Test
	void aClosedDayNeedsNothingAndTheOpenDaysCarryTheRest() {
		// Thanksgiving (Thu Nov 26) is checked on Settings; Fri Nov 27 is marked closed on the calendar.
		closedDays.setHoliday(new ClosedDayService.HolidayInput(Holiday.THANKSGIVING, true));
		closedDayRepo.save(new ClosedDay(LocalDate.of(2026, 11, 27), true, "Snow day", null, LocalDateTime.now()));
		GoalsService.GoalsView v = goals.view(nov, today);
		GoalsService.DayNeed thu = need(v, LocalDate.of(2026, 11, 26));
		GoalsService.DayNeed fri = need(v, LocalDate.of(2026, 11, 27));
		assertThat(thu.hours()).isEqualByComparingTo("0.0");
		assertThat(thu.sales()).isEqualByComparingTo("0");
		assertThat(thu.waxes()).isZero();
		assertThat(thu.closedFor()).isEqualTo("Thanksgiving");
		assertThat(fri.closedFor()).isEqualTo("Snow day");
		assertThat(need(v, LocalDate.of(2026, 11, 21)).closedFor()).isNull();
		assertThat(v.daysLeft()).isEqualTo(9);
		// 11,000 over 440 - 80 = 360 hours. Saturday: 11,000 × 60/360.
		assertThat(need(v, LocalDate.of(2026, 11, 21)).sales()).isEqualByComparingTo("1833");
		assertThat(v.days().stream().mapToInt(GoalsService.DayNeed::waxes).sum()).isEqualTo(56);
	}

	@Test
	void ownersSetGoalsAndSettingsDecideWhoSeesThem() throws Exception {
		mvc.perform(put("/api/owner/goals/2026-12").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"sales\":45000,\"waxes\":70,\"treatments\":null,\"ppg\":12.5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sales").value(45000))
			.andExpect(jsonPath("$.treatments").doesNotExist());
		mvc.perform(get("/api/owner/goals/2026-12").cookie(owner)).andExpect(jsonPath("$.waxes").value(70));
		mvc.perform(put("/api/owner/goals/2026-12").cookie(login("max@grove.test")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"sales\":1}")).andExpect(status().isForbidden());

		// Managers see the card by default; stylists don't until the owner allows it.
		mvc.perform(get("/api/goals").cookie(login("max@grove.test"))).andExpect(status().isOk());
		Cookie stylist = login("ava@grove.test");
		mvc.perform(get("/api/goals").cookie(stylist)).andExpect(status().isForbidden());
		mvc.perform(put("/api/owner/permissions").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":{\"VIEW_GOALS\":[\"STYLIST\",\"MANAGER\"]}}")).andExpect(status().isOk());
		mvc.perform(get("/api/goals").cookie(stylist)).andExpect(status().isOk());
		mvc.perform(get("/api/me/permissions").cookie(stylist)).andExpect(jsonPath("$.viewGoals").value(true));
	}

	@Test
	void anOpenTimecardKeepsTheLastCounts() throws Exception {
		org.mockito.Mockito.when(salonUltimate.detailedServiceByEmployee(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
			.thenReturn("Could Not Display Report. There are unclosed time cards for the following employees: Ava Stone. Close them.");
		goals.refreshCounts(nov, today.plusDays(1));
		assertThat(goals.counts(nov).waxes()).isEqualTo(4);
		assertThat(goals.counts(nov).through()).isEqualTo(LocalDate.of(2026, 11, 19));
	}

	@Test
	void apportionAddsUpExactly() {
		int[] out = GoalsService.apportion(10, List.of(new BigDecimal("3"), new BigDecimal("3"), new BigDecimal("3")));
		assertThat(out[0] + out[1] + out[2]).isEqualTo(10);
		assertThat(GoalsService.apportion(5, List.of(BigDecimal.ZERO, BigDecimal.ONE))).containsExactly(0, 5);
	}

	private static GoalsService.GoalLine line(GoalsService.GoalsView v, String key) {
		return v.lines().stream().filter(l -> l.key().equals(key)).findFirst().orElseThrow();
	}

	private static GoalsService.DayNeed need(GoalsService.GoalsView v, LocalDate date) {
		return v.days().stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
	}

	private static DailySales day(LocalDate date, int hours, String net, String retail) {
		DailySales d = new DailySales(date);
		d.setStatus(DailySales.Status.OK);
		d.setStaffWorked(4);
		d.setNetServiceSales(new BigDecimal(net));
		d.setRetailSales(new BigDecimal(retail));
		d.setFloorHours(BigDecimal.valueOf(hours));
		d.setClients(hours * 2);
		d.setAttemptedAt(LocalDateTime.now());
		return d;
	}

	private Shift shift(LocalDate date, int from, int to) {
		Shift s = new Shift();
		s.setUserId(stylistId);
		s.setDate(date);
		s.setStart(LocalTime.of(from, 0));
		s.setEnd(LocalTime.of(to, 0));
		return s;
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
