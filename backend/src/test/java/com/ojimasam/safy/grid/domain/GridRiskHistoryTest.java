package com.ojimasam.safy.grid.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.analysis.domain.risk.GridObservation;
import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.analysis.domain.risk.GridRiskAssessor;
import com.ojimasam.safy.analysis.domain.risk.GridRiskInput;
import com.ojimasam.safy.analysis.domain.risk.GridRiskSnapshot;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.place.domain.type.CongestionLevel;

class GridRiskHistoryTest {
	private static final Instant WINDOW_END = Instant.parse("2026-08-03T12:00:00Z");
	private static final Instant WINDOW_START = WINDOW_END.minus(Duration.ofMinutes(15));

	@Test
	@DisplayName("계산 결과와 시간 창을 그대로 옮긴다")
	void copiesSnapshotAndWindow() {
		GridRiskSnapshot snapshot = GridRiskAssessor.assess(new GridRiskInput(
				7L,
				List.of(new GridObservation(1L, 100L, 75.0, 0.86)),
				CongestionLevel.NORMAL
		));

		GridRiskHistory history = GridRiskHistory.of(snapshot, WINDOW_START, WINDOW_END);

		assertThat(history.getGridCellId()).isEqualTo(7L);
		assertThat(history.getScore()).isEqualTo(75.0);
		assertThat(history.getCongestionLevel()).isEqualTo(RiskLevel.HIGH);
		assertThat(history.getObservationStatus()).isEqualTo(GridObservationStatus.REPORTED);
		assertThat(history.getEvidenceCount()).isEqualTo(2);
		assertThat(history.getDistinctReporterCount()).isEqualTo(1);
		assertThat(history.getConfidenceScore()).isEqualTo(0.43);
		assertThat(history.getWindowStartedAt()).isEqualTo(WINDOW_START);
		assertThat(history.getWindowEndedAt()).isEqualTo(WINDOW_END);
		assertThat(history.getCalculationVersion()).isEqualTo(GridRiskAssessor.CALCULATION_VERSION);
	}

	@Test
	@DisplayName("근거가 사라진 격자는 NO_DATA 이력으로 남긴다")
	void recordsTerminationWhenEvidenceIsGone() {
		GridRiskSnapshot noData = GridRiskAssessor.assess(new GridRiskInput(7L, List.of(), null));
		assertThat(noData.observationStatus()).isEqualTo(GridObservationStatus.NO_DATA);

		GridRiskHistory history = GridRiskHistory.of(noData, WINDOW_START, WINDOW_END);

		// 0 으로 채우면 very_low(안전함)로 읽혀 데이터 없음과 구분되지 않는다.
		assertThat(history.getScore()).isNull();
		assertThat(history.getCongestionLevel()).isNull();
		// 지도에 그리지 않아야 하는 이력이다.
		assertThat(history.isActive()).isFalse();
	}

	@Test
	@DisplayName("근거가 있는 이력은 활성이다")
	void activeWhenEvidenceExists() {
		GridRiskSnapshot snapshot = GridRiskAssessor.assess(
				new GridRiskInput(7L, List.of(), CongestionLevel.CROWDED));

		assertThat(GridRiskHistory.of(snapshot, WINDOW_START, WINDOW_END).isActive()).isTrue();
	}

	@Test
	@DisplayName("시간 창이 없으면 만들 수 없다")
	void requiresWindow() {
		GridRiskSnapshot snapshot = GridRiskAssessor.assess(
				new GridRiskInput(7L, List.of(), CongestionLevel.CROWDED));

		assertThatThrownBy(() -> GridRiskHistory.of(snapshot, null, WINDOW_END))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GridRiskHistory.of(snapshot, WINDOW_START, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
