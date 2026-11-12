package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.WeeklyStat;

/** The matrix rules (same PPH as the payroll skill), on a small made-up matrix. */
class PayServiceTest {

	static final List<PayMatrixRow> MATRIX = List.of(
		row("0", "16"), row("38.5", "16"), row("39", "16.25"), row("39.5", "16.5"), row("40", "16.75"));
	static final BigDecimal MIN = new BigDecimal("16.00");

	@Test
	void pphCountsAFifthOfDiscounts() {
		// (1318.50 + (1403 − 1318.50) × 0.2) ÷ 33.89 = 1335.40 ÷ 33.89
		assertThat(PayService.pph(week("33.89", "1403.00", "1318.50"))).isEqualByComparingTo("39.40");
		assertThat(PayService.pph(week("0", "500", "500"))).isEqualByComparingTo("0");
	}

	@Test
	void rateIsTheHighestStepAtOrBelowPph() {
		assertThat(PayService.rate(new BigDecimal("30"), MATRIX, MIN, BigDecimal.ZERO)).isEqualByComparingTo("16.00");
		assertThat(PayService.rate(new BigDecimal("39.40"), MATRIX, MIN, BigDecimal.ZERO)).isEqualByComparingTo("16.25");
		assertThat(PayService.rate(new BigDecimal("39.50"), MATRIX, MIN, BigDecimal.ZERO)).isEqualByComparingTo("16.50");
		assertThat(PayService.rate(new BigDecimal("75"), MATRIX, MIN, BigDecimal.ZERO)).isEqualByComparingTo("16.75");
	}

	@Test
	void justUnderAStepStaysOnTheLowerRate() {
		// 1169.86 ÷ 30 = 38.9953…: shows as 39.00 but pays the 38.5 step, like payroll.
		WeeklyStat s = week("30", "1169.86", "1169.86");
		assertThat(PayService.pph(s)).isEqualByComparingTo("39.00");
		assertThat(PayService.rate(PayService.exactPph(s), MATRIX, MIN, BigDecimal.ZERO)).isEqualByComparingTo("16.00");
	}

	@Test
	void neverUnderTheMinimumAndBumpsGoOnTop() {
		// Below every step, or a step under the minimum, pays the minimum.
		assertThat(PayService.rate(new BigDecimal("10"), MATRIX, new BigDecimal("17"), BigDecimal.ZERO)).isEqualByComparingTo("17.00");
		assertThat(PayService.rate(new BigDecimal("40"), MATRIX, new BigDecimal("17"), BigDecimal.ZERO)).isEqualByComparingTo("17.00");
		// A lead's $0.50 goes on after the minimum.
		assertThat(PayService.rate(new BigDecimal("10"), MATRIX, MIN, new BigDecimal("0.50"))).isEqualByComparingTo("16.50");
		assertThat(PayService.rate(new BigDecimal("40"), MATRIX, MIN, new BigDecimal("1.00"))).isEqualByComparingTo("17.75");
	}

	private static PayMatrixRow row(String pph, String rate) {
		return new PayMatrixRow(new BigDecimal(pph), new BigDecimal(rate));
	}

	private static WeeklyStat week(String hours, String gross, String net) {
		WeeklyStat s = new WeeklyStat();
		s.setFloorHours(new BigDecimal(hours));
		s.setServiceSales(new BigDecimal(gross));
		s.setNetServiceSales(new BigDecimal(net));
		return s;
	}
}
