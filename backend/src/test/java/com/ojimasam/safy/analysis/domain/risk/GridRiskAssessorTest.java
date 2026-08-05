package com.ojimasam.safy.analysis.domain.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.report.application.PublicReportQueryService;

/**
 * 격자 명세 10장의 테스트 벡터다.
 *
 * <p>이 테스트가 실패하면 벡터가 아니라 Java 코드를 고친다. 벡터는 위험도 공식을
 * 설계한 쪽이 정한 값이다.
 */
class GridRiskAssessorTest {

	private static final double DELTA = 0.005;
	private static final Long GRID = 1L;

	@Test
	@DisplayName("G1 근거가 없으면 score 는 0 이 아니라 null 이다")
	void g1_noData() {
		GridRiskSnapshot snapshot = assess(List.of(), null);

		assertNull(snapshot.score());
		assertNull(snapshot.congestionLevel());
		assertEquals(GridObservationStatus.NO_DATA, snapshot.observationStatus());
		assertEquals(0.0, snapshot.confidenceScore(), DELTA);
		assertEquals(0, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("G2 121곳 붐빔만 있으면 medium 이다 (공공데이터 단독 상한)")
	void g2_crowdedOnly() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.MEDIUM, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(0.5, snapshot.confidenceScore(), DELTA);
		assertEquals(1, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("G3 121곳 여유만 있으면 very_low 다")
	void g3_relaxedOnly() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.RELAXED);

		assertEquals(10.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.VERY_LOW, snapshot.congestionLevel());
	}

	@Test
	@DisplayName("G4 제보 1건만이면 신뢰도가 절반 이하다")
	void g4_singleReport() {
		GridRiskSnapshot snapshot = assess(List.of(report(1L, 100L, 75.0, 0.86)), null);

		assertEquals(75.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.HIGH, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
		// 0.3 + 0.2 × 1 = 0.5, 여기에 제보 신뢰도 0.86 을 곱한다.
		assertEquals(0.43, snapshot.confidenceScore(), DELTA);
		assertEquals(1, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G5 121곳이 제보보다 높으면 공공데이터가 바닥선이 된다")
	void g5_publicDataWins() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 30.0, 0.86)), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(0.5, snapshot.confidenceScore(), DELTA);
		// 제보가 있었다는 사실은 근거 수와 제보자 수에 남는다.
		assertEquals(2, snapshot.evidenceCount());
		assertEquals(1, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G6 제보자가 여러 명이면 신뢰도가 올라간다")
	void g6_multipleReporters() {
		GridRiskSnapshot snapshot = assess(
				List.of(
						report(1L, 100L, 75.0, 0.86),
						report(2L, 200L, 60.0, 0.90),
						report(3L, 300L, 40.0, 0.70)),
				CongestionLevel.NORMAL);

		assertEquals(75.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.HIGH, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
		// 0.3 + 0.2 × 3 = 0.9, 제보 신뢰도 최댓값 0.9 를 곱한다.
		assertEquals(0.81, snapshot.confidenceScore(), DELTA);
		// 제보 3건 + 121곳 관측 1
		assertEquals(4, snapshot.evidenceCount());
		assertEquals(3, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G7 제보 50 은 붐빔 60 을 넘지 못한다")
	void g7_publicDataIsFloor() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 50.0, 0.86)), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.MEDIUM, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(0.5, snapshot.confidenceScore(), DELTA);
		assertEquals(2, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("G8 finalRisk 가 null 인 제보는 계산에서 제외된다")
	void g8_unassessableReportIsExcluded() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, null, 0.0)),
				CongestionLevel.SLIGHTLY_CROWDED);

		assertEquals(45.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		// 121곳만 센다.
		assertEquals(1, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("같은 사람이 여러 건 올려도 제보자 수는 1이다")
	void sameReporterCountsOnce() {
		GridRiskSnapshot snapshot = assess(
				List.of(
						report(1L, 100L, 70.0, 0.86),
						report(2L, 100L, 65.0, 0.86),
						report(3L, 100L, 60.0, 0.86)),
				null);

		assertEquals(1, snapshot.distinctReporterCount());
		assertEquals(3, snapshot.evidenceCount());
		assertEquals(0.43, snapshot.confidenceScore(), DELTA);
	}

	@Test
	@DisplayName("제보와 121곳이 동점이면 REPORTED 다")
	void tieGoesToReported() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 60.0, 0.86)), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
	}

	@Test
	@DisplayName("계산 버전을 함께 기록한다")
	void recordsCalculationVersion() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.NORMAL);

		assertEquals("safy-grid-risk-v0.1", snapshot.calculationVersion());
	}

	@Test
	@DisplayName("제보 목록이 null 이어도 공공데이터로 계산한다")
	void handlesNullObservations() {
		GridRiskSnapshot snapshot = GridRiskAssessor.assess(
				new GridRiskInput(GRID, null, CongestionLevel.NORMAL));

		assertEquals(30.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
	}

	/**
	 * 두 창이 어긋나면 지도에 마커는 있는데 그 칸의 격자 위험도는 비어 있는 상태가 생긴다.
	 * 한쪽만 바꾸는 것을 막기 위해 같은 값임을 고정한다.
	 */
	@Test
	@DisplayName("근거 창이 시민 지도의 제보 마커 창과 같다")
	void windowMatchesPublicReportWindow() {
		assertEquals(
				PublicReportQueryService.RECENT_WINDOW,
				Duration.ofMinutes(GridRiskAssessor.WINDOW_MINUTES));
	}

	private static GridObservation report(
			long reportId, long reporterId, Double finalRisk, double confidence) {
		return new GridObservation(reportId, reporterId, finalRisk, confidence);
	}

	private static GridRiskSnapshot assess(
			List<GridObservation> reports, CongestionLevel publicLevel) {
		return GridRiskAssessor.assess(new GridRiskInput(GRID, reports, publicLevel));
	}
}
