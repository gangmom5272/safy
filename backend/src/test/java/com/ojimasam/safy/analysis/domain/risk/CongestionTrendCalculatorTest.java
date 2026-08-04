package com.ojimasam.safy.analysis.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class CongestionTrendCalculatorTest {
	@Test
	void usesSeventyFifthPercentileInsteadOfMaximumForStableScores() {
		List<FrameObservation> frames = observations(
				new double[] {10, 10, 10, 10, 10, 10, 100, 100},
				new double[] {20, 20, 20, 20, 20, 20, 90, 90});

		CongestionTrend trend = CongestionTrendCalculator.calculate(frames);

		assertThat(trend.peakVisualCongestion()).isEqualTo(10.0);
		assertThat(trend.peakLocalCongestion()).isEqualTo(20.0);
	}

	@Test
	void keepsConstantSeriesUnchanged() {
		List<FrameObservation> frames = observations(
				new double[] {30, 30, 30, 30, 30, 30},
				new double[] {75, 75, 75, 75, 75, 75});

		CongestionTrend trend = CongestionTrendCalculator.calculate(frames);

		assertThat(trend.peakVisualCongestion()).isEqualTo(30.0);
		assertThat(trend.peakLocalCongestion()).isEqualTo(75.0);
	}

	private List<FrameObservation> observations(double[] visual, double[] local) {
		Instant start = Instant.parse("2026-08-01T00:00:00Z");
		return java.util.stream.IntStream.range(0, visual.length)
				.mapToObj(index -> new FrameObservation((long) index + 1,
						start.plusSeconds(index * 5L), visual[index], local[index]))
				.toList();
	}
}
