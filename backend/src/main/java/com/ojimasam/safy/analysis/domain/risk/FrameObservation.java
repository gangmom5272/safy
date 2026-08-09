package com.ojimasam.safy.analysis.domain.risk;

import java.time.Instant;

public record FrameObservation(
		Long sourceMediaAssetId,
		Instant observedAt,
		double visualCongestionScore,
		double localPeakCongestionScore
) {
	public FrameObservation {
		if (sourceMediaAssetId == null || sourceMediaAssetId <= 0) {
			throw new IllegalArgumentException("원본 미디어 ID는 필수입니다.");
		}
		if (observedAt == null) throw new IllegalArgumentException("관측 시각은 필수입니다.");
	}
}
