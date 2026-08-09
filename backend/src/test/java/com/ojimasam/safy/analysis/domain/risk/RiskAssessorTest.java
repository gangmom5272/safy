package com.ojimasam.safy.analysis.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class RiskAssessorTest {
	@Test
	void calculatesFlatSeriesFromSmoothedPeakScores() {
		List<FrameObservation> frames = observations(
				new double[] {10, 10, 10, 10, 10, 10},
				new double[] {20, 20, 20, 20, 20, 20});

		RiskCalculation result = RiskAssessor.assess(frames, 6, 6, false);

		assertThat(result.finalRisk()).isEqualTo(16.0);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.VERY_LOW);
		assertThat(result.persistence()).isEqualTo(0.0);
		assertThat(result.growth()).isNull();
		assertThat(result.confirmRequired()).isTrue();
	}

	@Test
	void keepsTrendUnknownWhenThereAreFewerThanSixFrames() {
		List<FrameObservation> frames = observations(
				new double[] {17, 17, 17}, new double[] {37.5, 37.5, 37.5});

		RiskCalculation result = RiskAssessor.assess(frames, 3, 3, false);

		assertThat(result.finalRisk()).isEqualTo(29.3);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
		assertThat(result.persistence()).isNull();
		assertThat(result.growth()).isNull();
		assertThat(result.confirmRequired()).isTrue();
	}

	@Test
	void doesNotRaiseSingleImageToHighFromLocalPeakAlone() {
		List<FrameObservation> frames = observations(
				new double[] {12.81}, new double[] {90.0});

		RiskCalculation result = RiskAssessor.assess(frames, 1, 1, false);

		assertThat(result.finalRisk()).isEqualTo(59.1);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
		assertThat(result.persistence()).isNull();
		assertThat(result.confirmRequired()).isTrue();
		assertThat(result.riskRuleVersion()).isEqualTo("safy-risk-v0.3");
	}

	@Test
	void keepsHighRiskForSingleImageWhenVisualAndLocalScoresAreBothHigh() {
		List<FrameObservation> frames = observations(
				new double[] {60.0}, new double[] {90.0});

		RiskCalculation result = RiskAssessor.assess(frames, 1, 1, false);

		assertThat(result.finalRisk()).isEqualTo(78.0);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
		assertThat(result.confirmRequired()).isTrue();
	}

	@Test
	void keepsUncorroboratedSustainedLocalCongestionAtMedium() {
		List<FrameObservation> frames = observations(
				new double[] {0, 0, 0, 0, 0, 0},
				new double[] {80, 80, 80, 80, 80, 80});

		RiskCalculation result = RiskAssessor.assess(frames, 6, 6, false);

		assertThat(result.persistence()).isEqualTo(50.0);
		assertThat(result.finalRisk()).isEqualTo(55.5);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
		assertThat(result.confirmRequired()).isTrue();
	}

	@Test
	void capsWidePersistentParkSceneAtMediumAndRequiresConfirmation() {
		List<FrameObservation> frames = observations(
				new double[] {10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10},
				new double[] {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90});

		RiskCalculation result = RiskAssessor.assess(frames, 12, 12, false);

		assertThat(result.persistence()).isEqualTo(100.0);
		assertThat(result.growth()).isEqualTo(0.0);
		assertThat(result.finalRisk()).isEqualTo(60.0);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
		assertThat(result.confirmRequired()).isTrue();
	}

	@Test
	void keepsBroadPersistentCrowdAtVeryHigh() {
		List<FrameObservation> frames = observations(
				new double[] {45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45},
				new double[] {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90});

		RiskCalculation result = RiskAssessor.assess(frames, 12, 12, false);

		assertThat(result.finalRisk()).isEqualTo(87.0);
		assertThat(result.riskLevel()).isEqualTo(RiskLevel.VERY_HIGH);
		assertThat(result.confirmRequired()).isFalse();
	}

	@Test
	void allowsGrowingLocalCongestionToExceedMedium() {
		List<FrameObservation> frames = observations(
				new double[] {10, 10, 10, 10, 10, 10, 15, 15, 15, 15, 15, 15},
				new double[] {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90});

		RiskCalculation result = RiskAssessor.assess(frames, 12, 12, false);

		assertThat(result.growth()).isGreaterThanOrEqualTo(30.0);
		assertThat(result.finalRisk()).isGreaterThan(60.0);
		assertThat(result.riskLevel()).isIn(RiskLevel.HIGH, RiskLevel.VERY_HIGH);
	}

	@Test
	void doesNotTreatMissingAnalysisAsZeroCongestion() {
		RiskCalculation result = RiskAssessor.assess(List.of(), 4, 0, false);

		assertThat(result.assessable()).isFalse();
		assertThat(result.finalRisk()).isNull();
		assertThat(result.riskConfidence()).isZero();
		assertThat(result.confirmRequired()).isTrue();
	}

	private List<FrameObservation> observations(double[] visual, double[] local) {
		Instant start = Instant.parse("2026-08-01T00:00:00Z");
		return java.util.stream.IntStream.range(0, visual.length)
				.mapToObj(index -> new FrameObservation((long) index + 1,
						start.plusSeconds(index * 5L), visual[index], local[index]))
				.toList();
	}
}
