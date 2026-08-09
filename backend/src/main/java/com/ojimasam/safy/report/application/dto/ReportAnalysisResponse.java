package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

/**
 * 제보 단위 AI 분석 결과.
 *
 * <p>공공데이터는 담지 않는다. 이 응답은 그 제보를 분석한 결과만 다루고, 지역 단위
 * 서울시 실시간 혼잡도는 지역 상황 요약(`AreaStatusResponse`)이 담당한다.
 *
 * @param peakLocalCongestionScore 제보 단위로 안정화한 국소 최대 혼잡도(stableL).
 *        이름은 peak 이지만 프레임 L 의 최댓값이 아니라 평활 후 75백분위수다.
 */
public record ReportAnalysisResponse(
		Long reportId,
		String status,
		String summary,
		String riskLevel,
		Double riskScore,
		Integer crowdCount,
		Double confidence,
		Boolean confirmRequired,
		Double peakVisualCongestionScore,
		Double peakLocalCongestionScore,
		Double persistenceScore,
		Double growthScore,
		Integer totalFrameCount,
		Integer completedFrameCount,
		Integer invalidFrameCount,
		Integer failedFrameCount,
		String riskRuleVersion,
		Instant analyzedAt
) {
	public static ReportAnalysisResponse pending(Long reportId) {
		return new ReportAnalysisResponse(reportId, "NOT_STARTED", null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null);
	}
}
