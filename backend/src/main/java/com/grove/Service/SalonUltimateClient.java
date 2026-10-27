package com.grove.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs in to Salon Ultimate and runs the "Calculate Gross Pay" report for one
 * week, the same way the payroll skill does (plain form posts, no browser).
 * A fresh session per pull, so no cookies are kept between runs.
 */
@Service
public class SalonUltimateClient {

	/** The report columns the payroll skill asks for, in the same order. */
	private static final String[] COLUMNS = {
		"floorHours", "offFloorHours", "unpaidTips", "productPerGuest",
		"retailSales", "retailCommUnpaid", "preDiscountServiceSales", "discountedServiceSales",
	};
	private static final DateTimeFormatter US = DateTimeFormatter.ofPattern("MM/dd/yyyy");
	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

	/** Thrown when Salon Ultimate rejects the username/password. */
	public static class LoginFailed extends IOException {
		LoginFailed() { super("Salon Ultimate didn't accept the username and password"); }
	}

	private final String baseUrl;
	private final String username;
	private final String password;

	public SalonUltimateClient(
			@Value("${app.salon-ultimate.url:https://fsnagel.salonultimate.com}") String baseUrl,
			@Value("${app.salon-ultimate.username:}") String username,
			@Value("${app.salon-ultimate.password:}") String password) {
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.username = username;
		this.password = password;
	}

	public boolean isConfigured() {
		return !username.isBlank() && !password.isBlank();
	}

	/** The report's HTML for {@code start} through {@code end}. */
	public String weekReport(LocalDate start, LocalDate end) throws IOException, InterruptedException {
		HttpClient http = HttpClient.newBuilder()
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15))
			.build();

		Map<String, String> login = new LinkedHashMap<>();
		login.put("_username", username);
		login.put("_password", password);
		HttpResponse<String> signedIn = http.send(post("/login_check", login, 15), HttpResponse.BodyHandlers.ofString());
		if (signedIn.uri().getPath().contains("login")) throw new LoginFailed();

		Map<String, String> report = new LinkedHashMap<>();
		report.put("payrollGroupId", "");
		report.put("reportName", "Calculate Gross Pay");
		report.put("report[isSortAscending]", "0");
		report.put("report[sortColumn]", "");
		report.put("report[employee]", "");
		report.put("report[startDate]", start.format(US));
		report.put("report[endDate]", end.format(US));
		report.put("report[startDate2]", "");
		report.put("report[endDate2]", "");
		report.put("report[payrollMode]", "1");
		report.put("report[nonPayrollEmployees]", "");
		report.put("report[separateByLocation]", "");
		report.put("report[printPayrollId]", "");
		for (int i = 0; i < 24; i++) {
			report.put("report[itemCodeCollection][" + i + "]", i < COLUMNS.length ? COLUMNS[i] : "");
		}
		HttpResponse<String> res = http.send(post("/report/displayPayroll", report, 60), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("Salon Ultimate answered " + res.statusCode());
		return res.body();
	}

	private HttpRequest post(String path, Map<String, String> form, int timeoutSeconds) {
		String body = form.entrySet().stream()
			.map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
			.collect(Collectors.joining("&"));
		return HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(Duration.ofSeconds(timeoutSeconds))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("User-Agent", USER_AGENT)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
