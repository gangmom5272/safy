package com.ojimasam.safy.report.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.report.application.dto.OperatorAssignmentResponse;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.application.ReportPublicVisibilityService;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.ReportTransferService;
import com.ojimasam.safy.report.application.dto.ReportDetailView;
import com.ojimasam.safy.report.application.dto.ReportTransferResponse;
import com.ojimasam.safy.report.application.dto.ReportTransferTargetResponse;
import com.ojimasam.safy.report.presentation.dto.OperatorReportTransferRequest;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.ojimasam.safy.report.presentation.dto.OperatorReportReleaseRequest;

@RestController
@RequestMapping("/api/v1/operator/reports")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "담당자 제보 처리")
public class OperatorReportController {
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportQueryService reportQueryService;
	private final ReportTransferService transferService;
	private final ReportPublicVisibilityService publicVisibilityService;

	public OperatorReportController(ReportOperatorAssignmentService assignmentService,
			ReportQueryService reportQueryService, ReportTransferService transferService,
			ReportPublicVisibilityService publicVisibilityService) {
		this.assignmentService = assignmentService;
		this.reportQueryService = reportQueryService;
		this.transferService = transferService;
		this.publicVisibilityService = publicVisibilityService;
	}

	@PostMapping("/{reportId}/withhold")
	@Operation(summary = "공개 제보 내리기",
			description = "업무 종료로 시민에게 공개된 제보를 부적절 판단 시 다시 비공개로 내립니다. "
					+ "제보 처리 상태와 라이브 세션에는 영향을 주지 않습니다. 담당했던 제보이거나 SUPER_ADMIN 이어야 합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "비공개 전환 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 제보에 대한 권한 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ResponseEntity<Void> withhold(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		publicVisibilityService.withhold(CurrentUser.id(jwt), reportId);
		return ResponseEntity.noContent().build();
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
			description = "현재 담당자가 맡은 제보를 조회합니다. status 를 지정하지 않으면 "
					+ "종료·반려·재배정으로 종결된 건까지 포함한 전체 담당 이력을 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배정 제보 목록 조회 성공")
	public ApiResponse<PageResponse<OperatorAssignmentResponse>> listMine(
			@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "배정 상태 필터. 생략하면 전체 이력을 조회합니다.")
			@RequestParam(required = false) Set<ReportOperatorAssignmentStatus> status,
			Pageable pageable
	) {
		return ApiResponse.success(PageResponse.from(
				assignmentService.listMine(CurrentUser.id(jwt), status, pageable)
		));
	}

	@PostMapping("/{reportId}/claim")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "공용 큐 제보 담당하기",
			description = "센터 공용 큐의 미배정 제보를 직접 담당합니다. 담당 즉시 내 관제 타일에 들어오며, "
					+ "동시에 다른 담당자가 먼저 담당했다면 409를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "담당하기 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 다른 담당자가 담당했거나 소속 관제센터의 제보가 아님", content = @Content)
	public ApiResponse<OperatorAssignmentResponse> claim(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(assignmentService.claim(CurrentUser.id(jwt), reportId));
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

	@GetMapping("/{reportId}/transfer-targets")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "이관 대상 관제센터 목록",
			description = "담당 중인 제보를 이관할 수 있는 관제센터 후보를 주 담당·우선순위 순으로 반환합니다. "
					+ "현재 센터는 제외하며, 고를 수 없는 센터도 사유를 표시할 수 있도록 함께 내려주고 "
					+ "선택 가능 여부는 transferable 로 판단합니다. 행정구역이 확정되지 않은 제보는 빈 배열입니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "현재 관제센터 소속 담당자가 아님", content = @Content)
	public ApiResponse<List<ReportTransferTargetResponse>> transferTargets(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		return ApiResponse.success(transferService.findTransferTargets(CurrentUser.id(jwt), reportId));
	}

	@PostMapping("/{reportId}/transfer")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "다른 관제센터로 이관",
			description = "담당 중인 제보를 같은 행정구역을 담당(커버리지 등록)하는 다른 관제센터로 이관합니다. "
					+ "이미 거쳐온 센터로는 근무중 담당자가 있을 때만 되돌릴 수 있으며, 이관 후 대상 센터에서 "
					+ "자동 배정을 시도하고 실패하면 대상 센터 공용 큐에 남습니다. 사유 필수. "
					+ "선택지는 transfer-targets 로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이관 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사유 누락 또는 대상 센터가 행정구역을 담당하지 않음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보 또는 배정 내역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이관할 수 없는 상태이거나 근무자 없는 거쳐온 센터", content = @Content)
	public ApiResponse<ReportTransferResponse> transfer(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId,
			@Valid @RequestBody OperatorReportTransferRequest request
	) {
		return ApiResponse.success(transferService.transferManually(
				CurrentUser.id(jwt), reportId, request.targetControlCenterId(), request.reason()));
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
