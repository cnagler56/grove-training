package com.grove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.ResultActions;

import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.Role;
import com.grove.Domain.StatsImport;
import com.grove.Domain.VacationPayRequest;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.AdpPersonRepository;
import com.grove.Repository.AppSettingRepository;
import com.grove.Repository.PayCodeRepository;
import com.grove.Repository.PayMatrixRepository;
import com.grove.Repository.PayrollNoteRepository;
import com.grove.Repository.StatsImportRepository;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;
import com.grove.Repository.VacationPayRequestRepository;
import com.grove.Repository.WeeklyStatRepository;
import com.grove.Service.AdpService;
import com.grove.Service.SessionService;

/** The ADP import file and its settings. Names, IDs, and the company code are made up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdpTests {

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired UserSessionRepository sessions;
	@Autowired WeeklyStatRepository stats;
	@Autowired StatsImportRepository imports;
	@Autowired PayMatrixRepository matrix;
	@Autowired PayCodeRepository codes;
	@Autowired AdpPersonRepository adp;
	@Autowired AppSettingRepository settings;
	@Autowired VacationPayRequestRepository vacationPay;
	@Autowired PayrollNoteRepository payrollNotes;
	@MockBean JavaMailSender mailSender;

	private Cookie owner;
	/** The last check in November: monthly bonuses go on it. */
	private final LocalDate check = LocalDate.of(2026, 11, 27);
	private final LocalDate week1 = LocalDate.of(2026, 11, 7);
	private final LocalDate week2 = LocalDate.of(2026, 11, 14);

	@BeforeEach
	void setUp() throws Exception {
		stats.deleteAll();
		imports.deleteAll();
		payrollNotes.deleteAll();
		vacationPay.deleteAll();
		matrix.deleteAll();
		codes.deleteAll();
		adp.deleteAll();
		settings.findAll().stream().filter(s -> s.getKey().startsWith("pay.") || s.getKey().startsWith("adp.")).forEach(settings::delete);
		sessions.deleteAll();
		users.deleteAll();
		owner = signUp("owner@grove.test", "Owner", "Person");
		// $16.50 from 39 PPH, $17.25 from 45 PPH. Stylist minimum $16, receptionists $13,
		// lead bump $0.50, lead/prod $0.75, manager $1.00.
		matrix.saveAll(List.of(new PayMatrixRow(new BigDecimal("39"), new BigDecimal("16.50")),
			new PayMatrixRow(new BigDecimal("45"), new BigDecimal("17.25"))));
	}

	@Test
	void theFileHasHoursAtTheStandardRateAndListsTheRestToTypeByHand() throws Exception {
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		person("lena@grove.test", "Lena", "Park", Role.LEAD);
		person("max@grove.test", "Max", "Hill", Role.MANAGER);
		person("dee@grove.test", "Dee", "Desk", Role.RECEPTIONIST);
		person("bo@grove.test", "Bo", "Boss", Role.GENERAL_MANAGER);
		// Ava: 30 PPH in week 1 ($16, her standard rate), 40 PPH in week 2 ($16.50). Every week
		// below also has 1.5 off-floor hours.
		stats.save(stat(week1, "Ava Stone", "10", "300", "80", "25.50"));
		stats.save(stat(week2, "Ava Stone", "10", "400", "20", "10"));
		// Lena (lead): 45 PPH, $17.25 + $0.50. Her off-floor hours pay $16 + $0.50.
		stats.save(stat(week1, "Lena Park", "10", "450", "0", "40"));
		// Max (manager): $16 + $1. His off-floor hours pay $16: the manager bump is floor hours only.
		stats.save(stat(week1, "Max Hill", "10", "300", "0", "0"));
		stats.save(stat(week1, "Dee Desk", "10", "0", "0", "0"));
		// Bo is paid a fixed amount in ADP: the file has his hours only.
		stats.save(stat(week1, "Bo Boss", "20", "300", "0", "12"));
		pulled(week1);
		pulled(week2);
		company("TESTCO1");
		adpFor("Ava Stone", "101", false, "200");
		adpFor("Lena Park", "102", false, null);
		adpFor("Max Hill", "103", false, null);
		adpFor("Dee Desk", "104", false, null);
		adpFor("Bo Boss", "105", true, null);
		pto("ava@grove.test", "8", VacationPayRequest.Status.APPROVED);
		note("ava@grove.test", "DEDUCTION", "25.50", "Uniform shirt");

		mvc.perform(get("/api/payroll/adp").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.problems.length()").value(0))
			.andExpect(jsonPath("$.lastCheckOfMonth").value(true))
			.andExpect(jsonPath("$.fileName").value("Grove-ADP-Biweekly-11072026-11202026.csv"))
			// By last name: Boss, Desk, Hill, Park, Stone.
			.andExpect(jsonPath("$.people[0].name").value("Boss, Bo"))
			.andExpect(jsonPath("$.people[0].salaried").value(true))
			.andExpect(jsonPath("$.people[0].adpRate").doesNotExist())
			.andExpect(jsonPath("$.people[2].name").value("Hill, Max"))
			.andExpect(jsonPath("$.people[2].adpRate").value(16.00))
			.andExpect(jsonPath("$.people[2].byHand[0].kind").value("REGULAR"))
			.andExpect(jsonPath("$.people[2].byHand[0].hours").value(10))
			.andExpect(jsonPath("$.people[2].byHand[0].rate").value(17.00))
			.andExpect(jsonPath("$.people[3].name").value("Park, Lena"))
			.andExpect(jsonPath("$.people[3].adpRate").value(16.50))
			.andExpect(jsonPath("$.people[3].byHand[0].rate").value(17.75))
			.andExpect(jsonPath("$.people[4].name").value("Stone, Ava"))
			.andExpect(jsonPath("$.people[4].adpRate").value(16.00))
			.andExpect(jsonPath("$.people[4].byHand.length()").value(2))
			.andExpect(jsonPath("$.people[4].byHand[0].hours").value(10))
			.andExpect(jsonPath("$.people[4].byHand[0].rate").value(16.50))
			.andExpect(jsonPath("$.people[4].byHand[1].kind").value("DEDUCTION"))
			.andExpect(jsonPath("$.people[4].byHand[1].dollars").value(25.5))
			.andExpect(jsonPath("$.people[4].byHand[1].note").value("Uniform shirt"))
			// 13 (Ava) + 1.5 (Max) + 1.5 (Lena) + 11.5 (Dee) + 21.5 (Bo).
			.andExpect(jsonPath("$.regularHours").value(49.0))
			.andExpect(jsonPath("$.vacationHours").value(8))
			.andExpect(jsonPath("$.tips").value(87.5))
			.andExpect(jsonPath("$.commission").value(8.00))
			.andExpect(jsonPath("$.bonus").value(200.00));

		String csv = mvc.perform(get("/api/payroll/adp/file").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Grove-ADP-Biweekly-11072026-11202026.csv")))
			.andReturn().getResponse().getContentAsString();
		String p = "TESTCO1,B,11/07/2026,11/20/2026,";
		assertThat(csv).isEqualTo(String.join("\r\n",
			"##GENERIC## V1.0",
			AdpServiceHeader.HEADER,
			p + "105,REG,21.50,,0,,BASE",
			p + "105,CASHTIP,,12.00,0,,BASE",
			p + "104,REG,11.50,,0,,BASE",
			p + "103,REG,1.50,,0,,BASE",
			p + "102,REG,1.50,,0,,BASE",
			p + "102,CASHTIP,,40.00,0,,BASE",
			p + "101,REG,13.00,,0,,BASE",
			p + "101,VAC,8.00,,0,,BASE",
			p + "101,CASHTIP,,35.50,0,,BASE",
			p + "101,COMM,,8.00,0,,BASE",
			p + "101,BON,,200.00,0,,BASE",
			""));
	}

	@Test
	void monthlyBonusesOnlyGoOnTheLastCheckOfTheMonth() throws Exception {
		LocalDate midMonth = LocalDate.of(2026, 11, 13);
		assertThat(AdpService.lastCheckOfMonth(midMonth)).isFalse();
		assertThat(AdpService.lastCheckOfMonth(check)).isTrue();
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		stats.save(stat(LocalDate.of(2026, 10, 24), "Ava Stone", "10", "300", "0", "0"));
		pulled(LocalDate.of(2026, 10, 24));
		pulled(LocalDate.of(2026, 10, 31));
		company("TESTCO1");
		adpFor("Ava Stone", "101", false, "200");

		mvc.perform(get("/api/payroll/adp").param("checkDate", midMonth.toString()).cookie(owner))
			.andExpect(jsonPath("$.lastCheckOfMonth").value(false))
			.andExpect(jsonPath("$.bonus").value(0))
			.andExpect(jsonPath("$.people[0].lines.length()").value(1));
	}

	@Test
	void theFileWaitsUntilEverythingIsReady() throws Exception {
		person("ava@grove.test", "Ava", "Stone", Role.STYLIST);
		stats.save(stat(week1, "Ava Stone", "10", "300", "0", "0"));
		// Cara isn't linked to a site account, so her rate can't be trusted.
		stats.save(stat(week1, "Cara Diaz", "10", "300", "0", "0"));
		pulled(week1);
		pto("ava@grove.test", "4", VacationPayRequest.Status.PENDING);

		mvc.perform(get("/api/payroll/adp").param("checkDate", check.toString()).cookie(owner))
			.andExpect(jsonPath("$.problems[0]").value(org.hamcrest.Matchers.startsWith("Add your ADP company code")))
			.andExpect(jsonPath("$.problems[1]").value(org.hamcrest.Matchers.startsWith("Week 2 hasn't been pulled")))
			.andExpect(jsonPath("$.problems[2]").value(org.hamcrest.Matchers.startsWith("Diaz, Cara: Not linked")))
			.andExpect(jsonPath("$.problems[3]").value(org.hamcrest.Matchers.startsWith("Add Cara Diaz's ADP employee ID")))
			.andExpect(jsonPath("$.problems[4]").value(org.hamcrest.Matchers.containsString("waiting for an answer")))
			.andExpect(jsonPath("$.problems[5]").value("Add Ava Stone's ADP employee ID on Settings."));
		mvc.perform(get("/api/payroll/adp/file").param("checkDate", check.toString()).cookie(owner))
			.andExpect(status().isConflict());
	}

	@Test
	void theOwnerSetsAdpDetailsAndIdsCantRepeat() throws Exception {
		stats.save(stat(week1, "Ava Stone", "10", "300", "0", "0"));
		stats.save(stat(week1, "Ben Cole", "10", "300", "0", "0"));

		company("TESTCO1").andExpect(jsonPath("$.companyCode").value("TESTCO1"));
		company("TEST CO").andExpect(status().isBadRequest());
		adpFor("Ava Stone", "101", false, "200")
			.andExpect(jsonPath("$.people[1].name").value("Ava Stone"))
			.andExpect(jsonPath("$.people[1].employeeId").value("101"))
			.andExpect(jsonPath("$.people[1].monthlyBonus").value(200))
			.andExpect(jsonPath("$.people[1].standardRate").value(16.00));
		adpFor("ben cole", "101", false, null).andExpect(status().isBadRequest());
		adpFor("Ben Cole", "8 6", false, null).andExpect(status().isBadRequest());
		adpFor("Ben Cole", "7", true, "-1").andExpect(status().isBadRequest());
		adpFor("Ben Cole", "7", true, null).andExpect(jsonPath("$.people[0].salaried").value(true));
		// Clearing everything removes the row.
		adpFor("Ava Stone", " ", false, "0").andExpect(jsonPath("$.people[1].employeeId").doesNotExist());
		assertThat(adp.findAll()).hasSize(1);

		Cookie gm = signUp("gm@grove.test", "Gia", "Ray");
		var u = users.findByEmail("gm@grove.test").orElseThrow();
		u.setRoles(Role.GENERAL_MANAGER);
		users.save(u);
		mvc.perform(get("/api/owner/adp").cookie(gm)).andExpect(status().isForbidden());
		mvc.perform(get("/api/payroll/adp").param("checkDate", check.toString()).cookie(gm)).andExpect(status().isForbidden());
	}

	/** ADP's header row, kept here so a change to it shows up as a failing test. */
	private static final class AdpServiceHeader {
		static final String HEADER = "IID,Pay Frequency,Pay Period Start,Pay Period End,Employee Id,Earnings Code,Pay Hours,Dollars,"
			+ "Separate Check,Worked In Dept,Rate Code";
	}

	private ResultActions company(String code) throws Exception {
		return mvc.perform(put("/api/owner/adp/company").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"companyCode\":\"" + code + "\"}"));
	}

	private ResultActions adpFor(String name, String id, boolean salaried, String bonus) throws Exception {
		return mvc.perform(put("/api/owner/adp/people").cookie(owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"employeeId\":" + (id == null ? "null" : "\"" + id + "\"")
				+ ",\"salaried\":" + salaried + ",\"monthlyBonus\":" + bonus + "}"));
	}

	private void pulled(LocalDate week) {
		imports.save(new StatsImport(week, StatsImport.Status.OK, null, 1, LocalDateTime.now()));
	}

	private void pto(String email, String hours, VacationPayRequest.Status s) {
		var r = new VacationPayRequest();
		r.setUserId(users.findByEmail(email).orElseThrow().getUserId());
		r.setHours(new BigDecimal(hours));
		r.setCheckDate(check);
		r.setStatus(s);
		vacationPay.save(r);
	}

	private void note(String email, String kind, String amount, String text) throws Exception {
		Long id = users.findByEmail(email).orElseThrow().getUserId();
		mvc.perform(post("/api/payroll/notes").cookie(owner).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + id + ",\"checkDate\":\"" + check + "\",\"kind\":\"" + kind + "\",\"amount\":" + amount
					+ ",\"note\":\"" + text + "\"}"))
			.andExpect(status().isCreated());
	}

	private void person(String email, String first, String last, Role role) throws Exception {
		signUp(email, first, last);
		var u = users.findByEmail(email).orElseThrow();
		u.setRoles(role);
		u.setSalonUltimateName(first + " " + last);
		users.save(u);
	}

	/** One week: floor hours, sales (gross = net), retail, tips; 1.5 off-floor hours and PPG 1.00 every time. */
	private static WeeklyStat stat(LocalDate week, String name, String hours, String sales, String retail, String tips) {
		WeeklyStat s = new WeeklyStat();
		s.setWeekStart(week);
		s.setSuName(name);
		s.setFloorHours(new BigDecimal(hours));
		s.setOffFloorHours(new BigDecimal("1.5"));
		s.setServiceSales(new BigDecimal(sales));
		s.setNetServiceSales(new BigDecimal(sales));
		s.setRetailSales(new BigDecimal(retail));
		s.setProductPerGuest(new BigDecimal("1.00"));
		s.setTips(new BigDecimal(tips));
		return s;
	}

	private Cookie signUp(String email, String first, String last) throws Exception {
		return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"teamCode\":\"test-code\",\"firstName\":\""
					+ first + "\",\"lastName\":\"" + last + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(SessionService.COOKIE_NAME);
	}
}
