package com.ojimasam.safy.realtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;

class RealtimeEventTest {
	@Test
	void createsAreaReportRequestWithoutReportId() {
		RealtimeEvent event = RealtimeEvent.create(
				10L, RealtimeEventType.AREA_REPORT_REQUEST, null, "50", null);

		assertThat(event.getReportId()).isNull();
		assertThat(event.getResourceId()).isEqualTo("50");
	}

	@Test
	void rejectsReportEventWithoutReportId() {
		assertThatThrownBy(() -> RealtimeEvent.create(
				10L, RealtimeEventType.REPORT_CLOSED, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
