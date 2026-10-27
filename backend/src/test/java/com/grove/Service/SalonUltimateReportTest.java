package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SalonUltimateReportTest {

	/** Made-up rows laid out like Salon Ultimate's "Calculate Gross Pay" table. */
	public static final String REPORT = """
		<table>
		<tr><td>Report period:</td><td>10/17/2026 - 10/23/2026</td></tr>
		<tr><th>x</th></tr>
		<tr><td>Method</td><td>Floor Hours</td><td>Off Floor Hours</td><td>Unpaid Tips</td><td>PPG</td>
		    <td>Retail Sales</td><td>Retail Comm.</td><td>Pre-Discount Service Sales</td><td>Discounted Service Sales</td></tr>
		<tr><td>H House _</td><td>0.00</td><td>0.00</td><td>0.00</td><td>24.84</td><td>124.20</td><td>0.00</td><td>0.00</td><td>0.00</td></tr>
		<tr><td>NO PAYROLL</td></tr>
		<tr><td>DELETED_7 Ava Stone</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td></tr>
		<tr><td>HOURLY</td></tr>
		<tr><td>7 Ava Stone</td><td>30.50</td><td>1.25</td><td>99.00</td><td>2.50</td><td>$80.00</td><td>8.00</td><td>1,200.00</td><td>1,150.50</td></tr>
		<tr><td>Mia Mia De La Cruz</td><td>10.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>400.00</td><td>390.00</td></tr>
		<tr><td>12 Off Week</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td><td>0.00</td></tr>
		<tr><td>Total Payroll Sales:</td><td>1600.00</td><td></td><td></td></tr>
		</table>
		""";

	@Test
	void readsEmployeesWhoWorkedAndSkipsTheRest() {
		List<SalonUltimateReport.Row> rows = SalonUltimateReport.parse(REPORT);

		assertThat(rows).extracting(SalonUltimateReport.Row::name).containsExactly("Ava Stone", "Mia De La Cruz");
		SalonUltimateReport.Row ava = rows.get(0);
		assertThat(ava.payrollId()).isEqualTo("7");
		assertThat(ava.floorHours()).isEqualByComparingTo("30.50");
		assertThat(ava.offFloorHours()).isEqualByComparingTo("1.25");
		assertThat(ava.tips()).isEqualByComparingTo("99.00");
		assertThat(ava.productPerGuest()).isEqualByComparingTo("2.50");
		assertThat(ava.retailSales()).isEqualByComparingTo("80.00");
		assertThat(ava.serviceSales()).isEqualByComparingTo("1200.00");
		assertThat(ava.netServiceSales()).isEqualByComparingTo("1150.50");
		assertThat(rows.get(1).payrollId()).isEqualTo("Mia");
	}

	@Test
	void spotsReportsBlockedByOpenTimecards() {
		String html = "<div>Could Not Display Report</div><p>There are unclosed time cards for the following employees: "
			+ "Ava Stone on 10/22/2026. Please close them.</p>";
		assertThat(SalonUltimateReport.parse(html)).isEmpty();
		assertThat(SalonUltimateReport.blockedBy(html)).isEqualTo("Ava Stone on 10/22/2026");
		assertThat(SalonUltimateReport.blockedBy(REPORT)).isNull();
	}

	@Test
	void readsMoneyFormats() {
		assertThat(SalonUltimateReport.number("(29.00)")).isEqualByComparingTo(new BigDecimal("-29.00"));
		assertThat(SalonUltimateReport.number("")).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(SalonUltimateReport.number("$1,403.00")).isEqualByComparingTo("1403.00");
	}
}
