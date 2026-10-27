package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.grove.Domain.PayCode;
import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.WeeklyStat;

/** Same rules as the payroll skill's calculate_payroll.py, on a small made-up matrix. */
class PayServiceTest {

	static final List<PayMatrixRow> MATRIX = List.of(
		row("0", "16"), row("38.5", "16"), row("39", "16.25"), row("39.5", "16.5"), row("40", "16.75"));

	@Test
	void pphCountsAFifthOfDiscounts() {
		// (1318.50 + (1403 − 1318.50) × 0.2) ÷ 33.89 = 1335.40 ÷ 33.89
		assertThat(PayService.pph(week("33.89", "1403.00", "1318.50"))).isEqualByComparingTo("39.40");
		assertThat(PayService.pph(week("0", "500", "500"))).isEqualByComparingTo("0");
	}

	@Test
	void rateIsTheHighestStepAtOrBelowPph() {
		assertThat(PayService.rate(new BigDecimal("30"), null, MATRIX)).isEqualByComparingTo("16.00");
		assertThat(PayService.rate(new BigDecimal("39.40"), null, MATRIX)).isEqualByComparingTo("16.25");
		assertThat(PayService.rate(new BigDecimal("39.50"), null, MATRIX)).isEqualByComparingTo("16.50");
		assertThat(PayService.rate(new BigDecimal("75"), null, MATRIX)).isEqualByComparingTo("16.75");
	}

	@Test
	void justUnderAStepStaysOnTheLowerRate() {
		// 1169.86 ÷ 30 = 38.9953…: shows as 39.00 but pays the 38.5 step, like payroll.
		WeeklyStat s = week("30", "1169.86", "1169.86");
		assertThat(PayService.pph(s)).isEqualByComparingTo("39.00");
		assertThat(PayService.rate(PayService.exactPph(s), null, MATRIX)).isEqualByComparingTo("16.00");
	}

	@Test
	void codesAddAPremiumOrReplaceTheMatrix() {
		assertThat(PayService.rate(new BigDecimal("40"), code("0.50", null), MATRIX)).isEqualByComparingTo("17.25");
		// A deduction can go below $16, but never below $13.
		assertThat(PayService.rate(new BigDecimal("40"), code("-3", null), MATRIX)).isEqualByComparingTo("13.75");
		assertThat(PayService.rate(new BigDecimal("10"), code("-3", null), MATRIX)).isEqualByComparingTo("13.00");
		assertThat(PayService.rate(new BigDecimal("80"), code(null, "42"), MATRIX)).isEqualByComparingTo("42.00");
	}

	@Test
	void codeFileNamesBecomeSalonUltimateNames() {
		assertThat(PayService.salonUltimateName("De La Cruz, Mia")).isEqualTo("Mia De La Cruz");
		assertThat(PayService.salonUltimateName("Stone, Ava")).isEqualTo("Ava Stone");
		assertThat(PayService.salonUltimateName("Cher")).isEqualTo("Cher");
	}

	private static PayMatrixRow row(String pph, String rate) {
		return new PayMatrixRow(new BigDecimal(pph), new BigDecimal(rate));
	}

	private static PayCode code(String premium, String flat) {
		return new PayCode("Someone", 1, premium == null ? null : new BigDecimal(premium), flat == null ? null : new BigDecimal(flat));
	}

	private static WeeklyStat week(String hours, String gross, String net) {
		WeeklyStat s = new WeeklyStat();
		s.setFloorHours(new BigDecimal(hours));
		s.setServiceSales(new BigDecimal(gross));
		s.setNetServiceSales(new BigDecimal(net));
		return s;
	}
}
