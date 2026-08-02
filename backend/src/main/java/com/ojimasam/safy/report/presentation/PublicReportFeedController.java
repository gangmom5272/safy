package com.ojimasam.safy.report.presentation;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.report.application.PublicReportQueryService;
import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;
import com.ojimasam.safy.report.application.dto.PublicReportView;
import com.ojimasam.safy.report.application.dto.ReportAnalysisResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 시민에게 공개된 제보만 다루는 피드. 담당자가 정상 제보로 판정(업무 종료)한 건만 노출되며,
 * 라이브 세션 정보와 원본 이미지는 이 경로로 나가지 않는다.
 */
@RestController
@RequestMapping("/api/v1/feed")
@Tag(name = "공개 제보 피드", description = "시민에게 공개된 제보 조회")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
public class PublicReportFeedController {
	private final PublicReportQueryService publicReportQueryService;

	public PublicReportFeedController(PublicReportQueryService publicReportQueryService) {
		this.publicReportQueryService = publicReportQueryService;
	}

	@GetMapping("/reports")
	@Operation(summary = "공개 제보 목록 조회", description = "공개된 제보를 최신순으로 커서 기반 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공개 제보 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 페이지 크기", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<PublicReportCursorResponse> reports(
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(publicReportQueryService.reports(cursor, size));
	}

	@GetMapping("/areas/{areaId}/reports")
	@Operation(summary = "행정구역별 공개 제보 목록 조회",
			description = "해당 행정구역에서 공개된 제보를 최신순으로 커서 기반 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역별 공개 제보 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 페이지 크기", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<PublicReportCursorResponse> areaReports(
			@PathVariable Long areaId,
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(publicReportQueryService.reportsByArea(areaId, cursor, size));
	}

	@GetMapping("/reports/{reportId}")
	@Operation(summary = "공개 제보 상세 조회",
			description = "공개된 제보의 상세를 조회합니다. 아직 공개되지 않았거나 내려간 제보는 404를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공개 제보 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "공개된 제보를 찾을 수 없음", content = @Content)
	public ApiResponse<PublicReportView> report(@PathVariable Long reportId) {
		return ApiResponse.success(publicReportQueryService.report(reportId));
	}

	@GetMapping("/reports/{reportId}/analysis")
	@Operation(summary = "공개 제보 분석 결과 조회",
			description = "공개된 제보의 AI 분석·위험도 결과를 조회합니다. 아직 결과가 없으면 NOT_STARTED를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 분석 결과 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "공개된 제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportAnalysisResponse> analysis(@PathVariable Long reportId) {
		return ApiResponse.success(publicReportQueryService.analysis(reportId));
	}
}
