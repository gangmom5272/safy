package com.ojimasam.safy.report.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.report.application.OperatorAssignmentResponse;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.ReportDetailView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/operator/reports")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "담당자 제보 처리")
public class OperatorReportController {
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportQueryService reportQueryService;

	public OperatorReportController(ReportOperatorAssignmentService assignmentService,
			ReportQueryService reportQueryService) {
		this.assignmentService = assignmentService;
		this.reportQueryService = reportQueryService;
	}

	@GetMapping("/{reportId}")
	@Operation(summary = "담당자 제보 상세 조회", description = "담당자가 처리 대상 제보의 상세 정보를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportDetailView> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId) {
		return ApiResponse.success(reportQueryService.getForOperator(CurrentUser.id(jwt), reportId));
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "내 배정 제보 목록 조회", description = "현재 담당자에게 배정되어 진행 중(ASSIGNED/ACCEPTED/IN_PROGRESS)인 제보를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배정 제보 목록 조회 성공")
	public ApiResponse<PageResponse<OperatorAssignmentResponse>> listMine(
			@AuthenticationPrincipal Jwt jwt,
			Pageable pageable
	) {
		return ApiResponse.success(PageResponse.from(
				assignmentService.listMine(CurrentUser.id(jwt), pageable)
		));
	}

	@PatchMapping("/{reportId}/accept")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "제보 수락", description = "배정받은 담당자가 제보를 수락합니다. (ASSIGNED → ACCEPTED)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 수락 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "수락할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> accept(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.accept(CurrentUser.id(jwt), reportId));
	}

	@PatchMapping("/{reportId}/start")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "업무 시작", description = "수락한 제보의 처리를 시작합니다. (ACCEPTED → IN_PROGRESS)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업무 시작 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "시작할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> start(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.start(CurrentUser.id(jwt), reportId));
	}

	@PatchMapping("/{reportId}/complete")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "업무 종료", description = "진행 중인 제보를 종료합니다. 제보가 CLOSED 되고 관제센터 라우팅도 종료됩니다. (IN_PROGRESS → CLOSED)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업무 종료 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "종료할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> complete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.complete(CurrentUser.id(jwt), reportId));
	}
}
