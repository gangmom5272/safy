package com.ojimasam.safy.analysis.domain.risk;

import java.util.List;

/**
 * 격자 1개의 위험도를 계산한다.
 *
 * <p>DB, Spring 에 의존하지 않는 순수 계산 클래스다. 근거가 없으면 0 이 아니라 null 을
 * 반환한다.
 *
 * <p>제보와 공공데이터를 가중평균하지 않고 <b>최댓값</b>을 쓴다. 커버리지가 서로 달라
 * (제보만·121곳만·둘 다·아무것도 없는 격자가 섞인다) 가중평균이면 한쪽이 없을 때마다
 * 재정규화해야 한다. 최댓값은 규칙이 단순하고 위험을 낮추는 방향으로 작동하지 않는다.
 */
public final class GridRiskAssessor {

	public static final String CALCULATION_VERSION = "safy-grid-risk-v0.1";
	public static final int WINDOW_MINUTES = 15;
	public static final double REPORTER_CONFIDENCE_BASE = 0.3;
	public static final double REPORTER_CONFIDENCE_STEP = 0.2;

	private GridRiskAssessor() {
	}

	public static GridRiskSnapshot assess(GridRiskInput input) {
		List<GridObservation> usable = input.reportObservations() == null
				? List.of()
				: input.reportObservations().stream()
						.filter(observation -> observation.finalRisk() != null)
						.toList();

		Double reportRisk = usable.stream()
				.map(GridObservation::finalRisk)
				.max(Double::compare)
				.orElse(null);

		Double publicRisk = input.publicCongestion() == null
				? null
				: PublicCongestionScore.of(input.publicCongestion());

		int distinctReporters = (int) usable.stream()
				.map(GridObservation::reporterUserId)
				.distinct()
				.count();

		// 121곳 관측은 15분 창에 5건이 들어와도 1로 센다. 같은 소스의 반복이라
		// 여러 건으로 세면 근거가 많은 것처럼 오해된다.
		int evidenceCount = usable.size() + (publicRisk == null ? 0 : 1);

		if (reportRisk == null && publicRisk == null) {
			return new GridRiskSnapshot(
					input.gridCellId(), null, null,
					GridObservationStatus.NO_DATA,
					evidenceCount, distinctReporters, 0.0,
					CALCULATION_VERSION);
		}

		double score;
		GridObservationStatus status;
		double confidence;

		// 동점이면 제보를 따른다. 제보가 더 강한 근거다.
		boolean reportWins = reportRisk != null
				&& (publicRisk == null || reportRisk >= publicRisk);

		if (reportWins) {
			score = reportRisk;
			status = GridObservationStatus.REPORTED;
			confidence = reporterFactor(distinctReporters) * maxRiskConfidence(usable);
		} else {
			score = publicRisk;
			status = GridObservationStatus.PUBLIC_DATA;
			confidence = PublicCongestionScore.PUBLIC_DATA_CONFIDENCE;
		}

		score = Math.max(0.0, Math.min(100.0, score));

		return new GridRiskSnapshot(
				input.gridCellId(),
				roundToTwoDecimals(score),
				RiskLevel.fromScore(score),
				status,
				evidenceCount,
				distinctReporters,
				roundToFourDecimals(Math.max(0.0, Math.min(1.0, confidence))),
				CALCULATION_VERSION);
	}

	/**
	 * 같은 사람이 3건 올린 것보다 3명이 각각 올린 것이 더 믿을 만하다.
	 * 1명 0.5 / 2명 0.7 / 3명 0.9 / 4명 이상 1.0
	 */
	private static double reporterFactor(int distinctReporters) {
		return Math.min(
				REPORTER_CONFIDENCE_BASE + REPORTER_CONFIDENCE_STEP * distinctReporters,
				1.0);
	}

	private static double maxRiskConfidence(List<GridObservation> usable) {
		return usable.stream()
				.mapToDouble(GridObservation::riskConfidence)
				.max()
				.orElse(0.0);
	}

	private static double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static double roundToFourDecimals(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}
}
