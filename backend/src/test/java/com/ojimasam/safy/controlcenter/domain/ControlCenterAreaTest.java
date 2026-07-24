package com.ojimasam.safy.controlcenter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

class ControlCenterAreaTest {
	@Test
	void assignsPrimaryCoverage() {
		Instant effectiveFrom = Instant.parse("2026-07-23T00:00:00Z");

		ControlCenterArea coverage = ControlCenterArea.assign(
				mock(ControlCenter.class),
				mock(AdministrativeArea.class),
				CoverageRole.PRIMARY,
				1,
				effectiveFrom,
				null
		);

		assertThat(coverage.getCoverageRole()).isEqualTo(CoverageRole.PRIMARY);
		assertThat(coverage.getPriority()).isEqualTo(1);
		assertThat(coverage.getStatus()).isEqualTo(CoverageStatus.ACTIVE);
	}

	@Test
	void rejectsEndBeforeStart() {
		Instant effectiveFrom = Instant.parse("2026-07-23T00:00:00Z");

		assertThatThrownBy(() -> ControlCenterArea.assign(
				mock(ControlCenter.class),
				mock(AdministrativeArea.class),
				CoverageRole.PRIMARY,
				1,
				effectiveFrom,
				effectiveFrom.minusSeconds(1)
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
