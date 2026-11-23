package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Reading Salon Ultimate's appointment book JSON. Made-up appointments in the real shape. */
class AppointmentBookTest {

	/** Two guests: one with a cut and a color (same group), one with a wax; plus a treatment add-on visit. */
	static final String BOOK = """
		{"response":{
		  "appointments":[
		    {"id":1,"appointmentGroupId":10,"price":25,"status":3,"service":{"myServiceCategoryName":"Haircut"}},
		    {"id":2,"appointmentGroupId":10,"price":85,"status":3,"service":{"myServiceCategoryName":"Color"}},
		    {"id":3,"appointmentGroupId":11,"price":18,"status":1,"service":{"myServiceCategoryName":"Waxing"}},
		    {"id":4,"appointmentGroupId":0,"price":20,"status":1,"service":{"myServiceCategoryName":"Treatment"}}
		  ],
		  "appointmentBooksStats":{"totalSales":148,"totalTime":"0.03:00:00","productivityPercent":40.0},
		  "date":"2026-11-23T00:00:00"},
		 "result":0}
		""";

	@Test
	void totalsServicesByCategoryAndCountsGuestsOnce() throws Exception {
		var service = new AppointmentBookService(null, "America/Chicago");
		var b = service.parse(LocalDate.of(2026, 11, 23), BOOK);
		assertThat(b.problem()).isNull();
		assertThat(b.projected()).isEqualByComparingTo("148");
		assertThat(b.services()).isEqualTo(4);
		assertThat(b.guests()).isEqualTo(3);
		assertThat(b.haircuts()).isEqualTo(1);
		assertThat(b.colors()).isEqualTo(1);
		assertThat(b.waxes()).isEqualTo(1);
		assertThat(b.treatments()).isEqualTo(1);
	}

	@Test
	void anUnexpectedShapeSaysSo() throws Exception {
		var service = new AppointmentBookService(null, "America/Chicago");
		assertThat(service.parse(LocalDate.of(2026, 11, 23), "{\"response\":{}}").problem()).isNotNull();
	}
}
