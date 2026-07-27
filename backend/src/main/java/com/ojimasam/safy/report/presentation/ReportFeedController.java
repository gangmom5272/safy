package com.ojimasam.safy.report.presentation;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.report.application.MyReportSummaryResponse;
import com.ojimasam.safy.report.application.ReportAnalysisResponse;
import com.ojimasam.safy.report.application.ReportCursorResponse;
import com.ojimasam.safy.report.application.ReportFeedQueryService;
import com.ojimasam.safy.report.application.ReportWebRtcResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "제보 피드", description = "프론트 지도/상황판 결합용 제보 조회")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class ReportFeedController {
	private final ReportFeedQueryService reportFeedQueryService;

	public ReportFeedController(ReportFeedQueryService reportFeedQueryService) {
		this.reportFeedQueryService = reportFeedQueryService;
	}

	@GetMapping("/recent")
	@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "최근 제보 목록 조회", description = "노출 가능 상태의 최근 제보를 커서 기반으로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "최근 제보 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<ReportCursorResponse> recent(
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(reportFeedQueryService.recent(cursor, size));
	}

	@GetMapping("/live")
	@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "라이브 제보 목록 조회", description = "라이브 유형 제보를 커서 기반으로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "라이브 제보 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<ReportCursorResponse> live(
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(reportFeedQueryService.live(cursor, size));
	}

	@GetMapping("/{reportId}/analysis")
	@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "제보 분석 결과 조회", description = "제보의 AI 분석 결과를 조회합니다. 미연동 상태에서는 NOT_STARTED를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 분석 결과 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportAnalysisResponse> analysis(@PathVariable Long reportId) {
		return ApiResponse.success(reportFeedQueryService.analysis(reportId));
	}

	@GetMapping("/{reportId}/webrtc")
	@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "제보 라이브 세션 정보 조회", description = "제보에 연결된 라이브 세션 정보와 입장 가능 여부를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 라이브 세션 정보 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportWebRtcResponse> webrtc(@PathVariable Long reportId) {
		return ApiResponse.success(reportFeedQueryService.webrtc(reportId));
	}

	@GetMapping("/me/summary")
	@PreAuthorize("hasRole('CITIZEN')")
	@Operation(summary = "내 제보 요약 조회", description = "본인 제보의 전체/진행/종료 건수를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 제보 요약 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<MyReportSummaryResponse> mySummary(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.success(reportFeedQueryService.mySummary(CurrentUser.id(jwt)));
	}
}
