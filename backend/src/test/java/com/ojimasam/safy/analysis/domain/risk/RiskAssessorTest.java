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
