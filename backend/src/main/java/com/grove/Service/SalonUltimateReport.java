package com.grove.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the table in Salon Ultimate's "Calculate Gross Pay" report (the same
 * report the payroll skill uses). Columns after the "Method" header:
 * Floor Hours, Off Floor Hours, Unpaid Tips, PPG, Retail Sales, Retail Comm.
 * Unpaid, Pre-Discount Service Sales, Discounted Service Sales. Each employee
 * row's first cell is "{payroll id} {First} {Last...}". Tips and commission are
 * pay details, so they're left out.
 */
public final class SalonUltimateReport {

	public record Row(
		String payrollId,
		String name,
		BigDecimal floorHours,
		BigDecimal offFloorHours,
		BigDecimal productPerGuest,
		BigDecimal retailSales,
		BigDecimal serviceSales,
		BigDecimal netServiceSales) {}

	private static final Pattern TR = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern TD = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private SalonUltimateReport() {}

	/**
	 * Employees who worked that week, one row each. Skips deleted employees,
	 * "_ House"-style placeholders, pay-method sub-rows ("HOURLY"), and totals.
	 */
	public static List<Row> parse(String html) {
		Map<String, Row> byName = new LinkedHashMap<>();
		boolean header = false;
		Matcher tr = TR.matcher(html);
		while (tr.find()) {
			List<String> cells = new ArrayList<>();
			Matcher td = TD.matcher(tr.group(1));
			while (td.find()) cells.add(clean(td.group(1)));
			if (cells.isEmpty()) continue;
			String first = cells.get(0);
			if (first.equals("Method")) { header = true; continue; }
			if (!header || cells.size() < 9) continue;
			if (first.startsWith("DELETED_") || first.contains(":")) continue;

			String[] parts = first.split("\\s+", 2);
			if (parts.length < 2 || !parts[1].contains(" ")) continue;
			String name = parts[1].trim();
			if (name.startsWith("_") || name.contains(" _") || name.endsWith("_")) continue;

			Row row = new Row(parts[0], name, number(cells.get(1)), number(cells.get(2)), number(cells.get(4)),
				number(cells.get(5)), number(cells.get(7)), number(cells.get(8)));
			boolean worked = row.floorHours().add(row.offFloorHours()).signum() > 0;
			if (worked) byName.putIfAbsent(name.toLowerCase(), row);
		}
		return List.copyOf(byName.values());
	}

	/**
	 * Salon Ultimate won't run the report while a timecard in the week is still
	 * open. Returns who/when it lists ("Jane Doe on 10/24/2026"), or null if not blocked.
	 */
	public static String blockedBy(String html) {
		if (!html.contains("unclosed time cards") && !html.contains("Could Not Display Report")) return null;
		int at = html.indexOf("There are unclosed time cards");
		if (at < 0) return "one or more employees have an unclosed timecard this week";
		String text = clean(html.substring(at, Math.min(html.length(), at + 800)).replaceAll("<[^>]+>", " "));
		Matcher m = Pattern.compile("for the following employees:\\s*(.*?)\\.\\s").matcher(text);
		return m.find() ? m.group(1).trim() : text.substring(0, Math.min(200, text.length()));
	}

	private static String clean(String fragment) {
		return fragment.replaceAll("<[^>]+>", "")
			.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'")
			.replaceAll("\\s+", " ").trim();
	}

	/** "1,403.00", "$92.80", "(29.00)", "" become numbers (blank = 0). */
	static BigDecimal number(String cell) {
		String s = cell.replace("$", "").replace(",", "").trim();
		boolean negative = s.startsWith("(") && s.endsWith(")");
		if (negative) s = s.substring(1, s.length() - 1);
		if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
		try {
			BigDecimal n = new BigDecimal(s);
			return negative ? n.negate() : n;
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}
}
