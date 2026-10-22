package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CelebrationServiceTest {

	@Test
	void leapDayFallsOnFebruary28InOtherYears() {
		LocalDate leap = LocalDate.of(2000, 2, 29);
		assertThat(CelebrationService.occurrence(leap, 2027)).isEqualTo(LocalDate.of(2027, 2, 28));
		assertThat(CelebrationService.occurrence(leap, 2028)).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(CelebrationService.occurrence(LocalDate.of(1990, 7, 14), 2026)).isEqualTo(LocalDate.of(2026, 7, 14));
	}
}
