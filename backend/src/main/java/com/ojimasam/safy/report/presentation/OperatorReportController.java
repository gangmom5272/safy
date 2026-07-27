package com.ojimasam.safy.report.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
import jakarta.validation.Valid;

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
	@Operation(summary = "담당자 제보 상세 조회",
			description = "담당자가 처리 대상 제보의 상세 정보를 조회합니다. 배정 담당자가 조회하면 자동으로 '처리중(확인)'으로 전이됩니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportDetailView> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId) {
		Long operatorId = CurrentUser.id(jwt);
		assignmentService.markHandling(operatorId, reportId);
		return ApiResponse.success(reportQueryService.getForOperator(operatorId, reportId));
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "내 배정 제보 목록 조회",
			description = "현재 담당자에게 배정되어 진행 중(ASSIGNED/IN_PROGRESS)인 제보를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배정 제보 목록 조회 성공")
	public ApiResponse<PageResponse<OperatorAssignmentResponse>> listMine(
			@AuthenticationPrincipal Jwt jwt,
			Pageable pageable
	) {
		return ApiResponse.success(PageResponse.from(
				assignmentService.listMine(CurrentUser.id(jwt), pageable)
		));
	}

	@PatchMapping("/{reportId}/complete")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "업무 종료",
			description = "제보를 종료합니다. 제보가 CLOSED 되고 관제센터 라우팅과 진행 중인 라이브 세션도 함께 종료되며 제보자에게 통지됩니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업무 종료 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "종료할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> complete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.complete(CurrentUser.id(jwt), reportId));
	}

	@PatchMapping("/{reportId}/reject")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "제보 반려",
			description = "무효·중복·오배정 등으로 제보를 대응 없이 종결(REJECTED)합니다. 라이브가 있으면 세션도 종료되고 제보자에게 통지됩니다. 사유 필수.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 반려 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "반려 사유 누락", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "반려할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> reject(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId,
			@Valid @RequestBody OperatorReportReleaseRequest request
	) {
		return ApiResponse.success(assignmentService.reject(CurrentUser.id(jwt), reportId, request.reason()));
	}

	@PatchMapping("/{reportId}/reassign")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "재배정 요청",
			description = "담당자가 처리를 놓고 같은 관제센터의 다른 담당자에게 재배정을 요청합니다. 제보는 유효한 상태로 유지되며 라이브 송출도 이어집니다. 사유 필수.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재배정 요청 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "재배정 사유 누락", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재배정할 수 없는 상태이거나 소속 담당자가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> reassign(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId,
			@Valid @RequestBody OperatorReportReleaseRequest request
	) {
		return ApiResponse.success(assignmentService.reassign(CurrentUser.id(jwt), reportId, request.reason()));
	}
}
