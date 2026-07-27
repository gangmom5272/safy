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
	public ApiResponse<ReportDetailView> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId) {
		return ApiResponse.success(reportQueryService.getForOperator(CurrentUser.id(jwt), reportId));
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('OPERATOR')")
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
	public ApiResponse<OperatorAssignmentResponse> accept(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.accept(CurrentUser.id(jwt), reportId));
	}

	@PatchMapping("/{reportId}/start")
	@PreAuthorize("hasRole('OPERATOR')")
	public ApiResponse<OperatorAssignmentResponse> start(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.start(CurrentUser.id(jwt), reportId));
	}

	@PatchMapping("/{reportId}/complete")
	@PreAuthorize("hasRole('OPERATOR')")
	public ApiResponse<OperatorAssignmentResponse> complete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.complete(CurrentUser.id(jwt), reportId));
	}
}
