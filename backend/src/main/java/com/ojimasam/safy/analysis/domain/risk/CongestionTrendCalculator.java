package com.ojimasam.safy.analysis.domain.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class CongestionTrendCalculator {
	public static final int NOMINAL_INTERVAL_SECONDS = 5;
	public static final int TREND_WINDOW_SECONDS = 30;
	public static final int PERSISTENCE_TARGET_SECONDS = 60;
	public static final int PERSISTENCE_MAX_GAP_SECONDS = 15;
	public static final double PERSISTENCE_THRESHOLD = 80.0;
	public static final int MINIMUM_FRAME_COUNT = 6;
	public static final int MINIMUM_FRAMES_PER_TREND_WINDOW = 2;

	private CongestionTrendCalculator() {
	}

	public static CongestionTrend calculate(List<FrameObservation> frames) {
		if (frames == null || frames.isEmpty()) return CongestionTrend.unknown();
		List<FrameObservation> ordered = new ArrayList<>(frames);
		ordered.sort(Comparator.comparing(FrameObservation::observedAt));
		double[] smoothedVisual = movingMedian(extractVisual(ordered));
		double[] smoothedLocalPeak = movingMedian(extractLocalPeak(ordered));
		return new CongestionTrend(
				calculatePersistence(ordered, smoothedLocalPeak),
				calculateGrowth(ordered, smoothedVisual),
				maximum(smoothedVisual),
				maximum(smoothedLocalPeak));
	}

	private static Double calculatePersistence(List<FrameObservation> ordered, double[] values) {
		if (ordered.size() < MINIMUM_FRAME_COUNT) return null;
		long longestSeconds = 0L;
		Instant runStart = null;
		Instant previous = null;
		for (int i = 0; i < ordered.size(); i++) {
			Instant current = ordered.get(i).observedAt();
			if (values[i] < PERSISTENCE_THRESHOLD) {
				runStart = null;
				previous = null;
				continue;
			}
			boolean gapTooLarge = previous != null
					&& secondsBetween(previous, current) > PERSISTENCE_MAX_GAP_SECONDS;
			if (runStart == null || gapTooLarge) runStart = current;
			long covered = secondsBetween(runStart, current) + NOMINAL_INTERVAL_SECONDS;
			longestSeconds = Math.max(longestSeconds, covered);
			previous = current;
		}
		return roundToOneDecimal(100.0 * Math.min((double) longestSeconds / PERSISTENCE_TARGET_SECONDS, 1.0));
	}

	private static Double calculateGrowth(List<FrameObservation> ordered, double[] values) {
		if (ordered.size() < MINIMUM_FRAME_COUNT) return null;
		Instant last = ordered.getLast().observedAt();
		List<Double> recent = new ArrayList<>();
		List<Double> baseline = new ArrayList<>();
		for (int i = 0; i < ordered.size(); i++) {
			long age = secondsBetween(ordered.get(i).observedAt(), last);
			if (age < TREND_WINDOW_SECONDS) recent.add(values[i]);
			else if (age < 2L * TREND_WINDOW_SECONDS) baseline.add(values[i]);
		}
		if (recent.size() < MINIMUM_FRAMES_PER_TREND_WINDOW
				|| baseline.size() < MINIMUM_FRAMES_PER_TREND_WINDOW) return null;
		double ratio = (mean(recent) + 1.0) / (mean(baseline) + 1.0);
		return roundToOneDecimal(Math.max(0.0, Math.min(100.0, (ratio - 1.0) * 100.0)));
	}

	private static double[] extractVisual(List<FrameObservation> frames) {
		double[] values = new double[frames.size()];
		for (int i = 0; i < frames.size(); i++) values[i] = frames.get(i).visualCongestionScore();
		return values;
	}

	private static double[] extractLocalPeak(List<FrameObservation> frames) {
		double[] values = new double[frames.size()];
		for (int i = 0; i < frames.size(); i++) values[i] = frames.get(i).localPeakCongestionScore();
		return values;
	}

	private static double[] movingMedian(double[] values) {
		double[] result = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			double[] window = Arrays.copyOfRange(values, Math.max(0, i - 1), Math.min(values.length, i + 2));
			Arrays.sort(window);
			int middle = window.length / 2;
			result[i] = window.length % 2 == 1
					? window[middle]
					: (window[middle - 1] + window[middle]) / 2.0;
		}
		return result;
	}

	private static Double maximum(double[] values) {
		if (values.length == 0) return null;
		double max = values[0];
		for (double value : values) max = Math.max(max, value);
		return max;
	}

	private static long secondsBetween(Instant from, Instant to) {
		return Duration.between(from, to).getSeconds();
	}

	private static double mean(List<Double> values) {
		double sum = 0.0;
		for (double value : values) sum += value;
		return sum / values.size();
	}

	private static double roundToOneDecimal(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
