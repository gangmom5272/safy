package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

/**
 * 시민에게 노출되는 제보. 목록과 상세가 같은 필드를 쓰므로 하나로 둔다.
 * 라이브 세션 정보(streamRoomId·streamStatus)는 담지 않는다 — 시민에게는 송출 중인 영상을
 * 노출하지 않고 블러 처리된 대표 이미지만 보여준다.
 */
public record PublicReportView(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		Long areaId,
		String areaCode,
		String areaName,
		Instant reportedAt,
		/**
		 * RiskLevel.jsonValue() 소문자 값(very_low~very_high). 아직 평가되지 않았으면 null 이다.
		 * 상세 분석 응답(ReportAnalysisResponse)과 같은 이름·형태를 쓴다 — 목록에만 없으면
		 * 지도 마커와 상세 화면의 위험도가 어긋난다.
		 */
		String riskLevel,
		Double riskScore,
		String blurredImageUrl,
		Instant blurredImageExpiresAt
) {
	public static PublicReportView of(Report report, RiskAssessment risk,
			String blurredImageUrl, Instant blurredImageExpiresAt) {
		AdministrativeArea area = report.getAdministrativeArea();
		return new PublicReportView(
				report.getId(),
				report.getType(),
				report.getCategory(),
				report.getSubcategory(),
				report.getDescription(),
				report.getLatitude(),
				report.getLongitude(),
				report.getAddress(),
				area == null ? null : area.getId(),
				area == null ? null : area.getCode(),
				area == null ? null : area.getName(),
				report.getReportedAt(),
				// 평가 전인 제보를 0점·최저 등급으로 채우지 않는다. 위험하지 않다는 뜻이 아니다.
				risk == null || risk.getRiskLevel() == null ? null : risk.getRiskLevel().jsonValue(),
				risk == null ? null : risk.getFinalRiskScore(),
				blurredImageUrl,
				blurredImageExpiresAt
		);
	}
}
