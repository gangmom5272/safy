package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ojimasam.safy.analysis.domain.risk.GridObservation;
import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.analysis.domain.risk.GridRiskAssessor;
import com.ojimasam.safy.analysis.domain.risk.GridRiskSnapshot;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.grid.domain.GridRiskHistory;
import com.ojimasam.safy.grid.domain.GridRiskHistoryRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;

class GridRiskAssessmentServiceTest {
	/** 초·나노를 일부러 붙였다. 창 끝이 분 단위로 잘리는지 보려면 필요하다. */
	private static final Instant NOW = Instant.parse("2026-08-03T12:03:47.512Z");
	private static final Instant WINDOW_END = Instant.parse("2026-08-03T12:03:00Z");
	// 창 길이를 여기 다시 적으면 상수를 바꿀 때 조용히 어긋난다. 이 테스트가 확인하는
	// 것은 창 끝을 분 단위로 자르는지이지 창 길이가 아니다.
	private static final Instant WINDOW_START =
			WINDOW_END.minus(Duration.ofMinutes(GridRiskAssessor.WINDOW_MINUTES));
	private static final Instant EARLIER_WINDOW_END = WINDOW_END.minus(Duration.ofMinutes(3));

	private final GridReportReader reportReader = mock(GridReportReader.class);
	private final GridCongestionReader congestionReader = mock(GridCongestionReader.class);
	private final GridRiskHistoryRepository histories = mock(GridRiskHistoryRepository.class);
	private final GridRiskAssessmentService service = new GridRiskAssessmentService(
			reportReader, congestionReader, histories,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("제보와 공공데이터가 있는 셀을 모두 계산한다")
	void assessesEveryCellWithEvidence() {
		givenReports(Map.of(7L, List.of(report(100L, 75.0, 0.86))));
		givenCongestion(Map.of(8L, CongestionLevel.CROWDED));
		givenNoHistory();

		assertThat(service.refresh()).isEqualTo(2);

		assertThat(captureSaved(2)).extracting(GridRiskHistory::getGridCellId)
				.containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	@DisplayName("한 셀에 제보와 공공데이터가 함께 있으면 높은 쪽을 쓴다")
	void takesHigherOfBothSources() {
		givenReports(Map.of(7L, List.of(report(100L, 30.0, 0.86))));
		givenCongestion(Map.of(7L, CongestionLevel.CROWDED));
		givenNoHistory();

		service.refresh();

		GridRiskHistory saved = captureSaved(1).get(0);
		assertThat(saved.getScore()).isEqualTo(60.0);
		assertThat(saved.getObservationStatus()).isEqualTo(GridObservationStatus.PUBLIC_DATA);
		// 제보가 있었다는 사실은 남는다.
		assertThat(saved.getEvidenceCount()).isEqualTo(2);
		assertThat(saved.getDistinctReporterCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("창 끝을 분 단위로 자른다")
	void truncatesWindowEndToMinute() {
		givenReports(Map.of());
		givenCongestion(Map.of(8L, CongestionLevel.NORMAL));
		givenNoHistory();

		service.refresh();

		verify(reportReader).observationsByGridCell(WINDOW_START, WINDOW_END);
		verify(congestionReader).latestByGridCell(WINDOW_START);

		GridRiskHistory saved = captureSaved(1).get(0);
		assertThat(saved.getWindowStartedAt()).isEqualTo(WINDOW_START);
		assertThat(saved.getWindowEndedAt()).isEqualTo(WINDOW_END);
	}

	@Test
	@DisplayName("값이 그대로면 새 행을 남기지 않는다")
	void skipsWhenNothingChanged() {
		givenReports(Map.of());
		givenCongestion(Map.of(8L, CongestionLevel.CROWDED));
		// 직전에도 붐빔이었다 — 혼잡도 등급은 3분마다 바뀌지 않는다.
		givenHistory(history(8L, 60.0, RiskLevel.MEDIUM,
				GridObservationStatus.PUBLIC_DATA, EARLIER_WINDOW_END));

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	@Test
	@DisplayName("점수가 바뀌면 새 행을 남긴다")
	void savesWhenScoreChanged() {
		givenReports(Map.of());
		givenCongestion(Map.of(8L, CongestionLevel.CROWDED));
		givenHistory(history(8L, 45.0, RiskLevel.MEDIUM,
				GridObservationStatus.PUBLIC_DATA, EARLIER_WINDOW_END));

		assertThat(service.refresh()).isEqualTo(1);

		GridRiskHistory saved = captureSaved(1).get(0);
		assertThat(saved.getScore()).isEqualTo(60.0);
		assertThat(saved.getWindowEndedAt()).isEqualTo(WINDOW_END);
	}

	@Test
	@DisplayName("점수가 같아도 출처가 바뀌면 새 행을 남긴다")
	void savesWhenOnlyStatusChanged() {
		// 제보 60 과 붐빔 60 은 동점이라 REPORTED 가 되고, 직전은 공공데이터였다.
		givenReports(Map.of(8L, List.of(report(100L, 60.0, 0.86))));
		givenCongestion(Map.of(8L, CongestionLevel.CROWDED));
		givenHistory(history(8L, 60.0, RiskLevel.MEDIUM,
				GridObservationStatus.PUBLIC_DATA, EARLIER_WINDOW_END));

		assertThat(service.refresh()).isEqualTo(1);
		assertThat(captureSaved(1).get(0).getObservationStatus())
				.isEqualTo(GridObservationStatus.REPORTED);
	}

	@Test
	@DisplayName("근거도 직전 이력도 없으면 아무것도 저장하지 않는다")
	void savesNothingWithoutEvidence() {
		givenReports(Map.of());
		givenCongestion(Map.of());
		givenNoHistory();

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	@Test
	@DisplayName("근거가 사라진 위험 셀은 NO_DATA 이력으로 종료한다")
	void terminatesCellWhoseEvidenceExpired() {
		// 직전 창에는 제보로 88점이었는데 15분이 지나 근거가 빠졌다.
		givenReports(Map.of());
		givenCongestion(Map.of());
		givenHistory(history(8L, 88.0, RiskLevel.VERY_HIGH,
				GridObservationStatus.REPORTED, EARLIER_WINDOW_END));

		assertThat(service.refresh()).isEqualTo(1);

		// 종료를 기록하지 않으면 마지막 위험도가 지도에 영구히 남는다.
		GridRiskHistory saved = captureSaved(1).get(0);
		assertThat(saved.getObservationStatus()).isEqualTo(GridObservationStatus.NO_DATA);
		assertThat(saved.getScore()).isNull();
		assertThat(saved.getCongestionLevel()).isNull();
		assertThat(saved.isActive()).isFalse();
	}

	@Test
	@DisplayName("이미 종료된 셀은 다시 종료하지 않는다")
	void doesNotTerminateTwice() {
		givenReports(Map.of());
		givenCongestion(Map.of());
		givenHistory(history(8L, null, null,
				GridObservationStatus.NO_DATA, EARLIER_WINDOW_END));

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	@Test
	@DisplayName("근거가 없던 셀은 처음부터 저장하지 않는다")
	void skipsCellThatNeverHadEvidence() {
		// 서울 전역 대부분이 이런 칸이다. 조회 시 행이 없으면 NO_DATA 로 본다.
		givenReports(Map.of(7L, List.of(report(100L, null, 0.0))));
		givenCongestion(Map.of());
		givenNoHistory();

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	@Test
	@DisplayName("분석되지 않은 제보만 있는 셀은 저장하지 않는다")
	void skipsCellWhereEveryReportIsUnassessable() {
		// 대상 셀에는 들어오지만 finalRisk 가 전부 null 이라 점수를 낼 수 없다.
		givenReports(Map.of(7L, List.of(report(100L, null, 0.0))));
		givenCongestion(Map.of());
		givenNoHistory();

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	@Test
	@DisplayName("같은 창을 다시 계산하면 기존 행을 갱신한다")
	void refreshesExistingRowForSameWindow() {
		givenReports(Map.of(7L, List.of(report(100L, 75.0, 0.86))));
		givenCongestion(Map.of());
		GridRiskHistory existing = history(7L, 30.0, RiskLevel.LOW,
				GridObservationStatus.REPORTED, WINDOW_END);
		givenHistory(existing);

		assertThat(service.refresh()).isEqualTo(1);

		// 새 행을 넣지 않고 기존 행의 값이 올라간다.
		verify(histories, never()).save(any());
		assertThat(existing.getScore()).isEqualTo(75.0);
		assertThat(existing.getCongestionLevel()).isEqualTo(RiskLevel.HIGH);
	}

	@Test
	@DisplayName("같은 창을 다시 계산했고 값도 같으면 아무것도 하지 않는다")
	void doesNothingWhenSameWindowAndSameValue() {
		givenReports(Map.of());
		givenCongestion(Map.of(8L, CongestionLevel.CROWDED));
		givenHistory(history(8L, 60.0, RiskLevel.MEDIUM,
				GridObservationStatus.PUBLIC_DATA, WINDOW_END));

		assertThat(service.refresh()).isZero();
		verify(histories, never()).save(any());
	}

	private void givenReports(Map<Long, List<GridObservation>> byCell) {
		when(reportReader.observationsByGridCell(any(), any())).thenReturn(byCell);
	}

	private void givenCongestion(Map<Long, CongestionLevel> byCell) {
		when(congestionReader.latestByGridCell(any())).thenReturn(byCell);
	}

	private void givenNoHistory() {
		when(histories.findLatestPerGridCell()).thenReturn(List.of());
	}

	private void givenHistory(GridRiskHistory... values) {
		when(histories.findLatestPerGridCell()).thenReturn(List.of(values));
	}

	private List<GridRiskHistory> captureSaved(int expectedCount) {
		ArgumentCaptor<GridRiskHistory> captor = ArgumentCaptor.forClass(GridRiskHistory.class);
		verify(histories, times(expectedCount)).save(captor.capture());
		return captor.getAllValues();
	}

	private static GridRiskHistory history(
			Long gridCellId, Double score, RiskLevel level,
			GridObservationStatus status, Instant windowEndedAt) {
		return GridRiskHistory.of(
				new GridRiskSnapshot(gridCellId, score, level, status, 1, 1, 0.5,
						"safy-grid-risk-v0.1"),
				windowEndedAt.minus(Duration.ofMinutes(15)),
				windowEndedAt);
	}

	private static GridObservation report(Long reporterId, Double finalRisk, double confidence) {
		return new GridObservation(1L, reporterId, finalRisk, confidence);
	}
}
