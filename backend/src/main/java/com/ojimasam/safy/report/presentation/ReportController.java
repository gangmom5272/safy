package com.ojimasam.safy.report.presentation;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.report.application.CreateReportCommand;
import com.ojimasam.safy.report.application.CreateReportResult;
import com.ojimasam.safy.report.application.CreateReportService;
import com.ojimasam.safy.report.application.ReportDetailView;
import com.ojimasam.safy.report.application.ReportListItemView;
import com.ojimasam.safy.report.application.ReportStatusHistoryResponse;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.CancelReportService;
import com.ojimasam.safy.report.presentation.docs.ReportExamples;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "시민 제보", description = "시민의 제보 등록·조회·취소")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@PreAuthorize("hasRole('CITIZEN')")
public class ReportController {
	private final CreateReportService createReportService;
	private final ReportQueryService reportQueryService;
	private final CancelReportService cancelReportService;

	public ReportController(CreateReportService createReportService, ReportQueryService reportQueryService,
			CancelReportService cancelReportService) {
		this.createReportService = createReportService;
		this.reportQueryService = reportQueryService;
		this.cancelReportService = cancelReportService;
	}

	@PatchMapping("/{reportId}/cancel")
	@Operation(summary = "제보 취소", description = "접수(RECEIVED) 또는 배정(ASSIGNED) 상태인 본인의 제보를 취소합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제보 취소 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "취소할 수 없는 상태의 제보", content = @Content)
	public ResponseEntity<Void> cancel(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		Long reporterId = CurrentUser.id(jwt);
		try {
			cancelReportService.cancel(reporterId, reportId);
			return ResponseEntity.noContent().build();
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
	}

	@GetMapping("/{reportId}")
	@Operation(summary = "내 제보 상세 조회", description = "본인이 등록한 제보의 상세 정보와 배정 관제센터를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<ReportDetailView>> getMine(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		Long reporterId = CurrentUser.id(jwt);
		return ResponseEntity.ok(ApiResponse.success(reportQueryService.getMine(reporterId, reportId)));
	}

	@GetMapping("/{reportId}/status-history")
	@Operation(summary = "내 제보 상태 변경 이력 조회", description = "본인 제보의 상태 전이 이력을 시간순으로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 이력 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<List<ReportStatusHistoryResponse>>> getMyStatusHistory(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		Long reporterId = CurrentUser.id(jwt);
		return ResponseEntity.ok(ApiResponse.success(
				reportQueryService.getMyStatusHistory(reporterId, reportId)
		));
	}

	@GetMapping("/me")
	@Operation(summary = "내 제보 목록 조회", description = "본인이 등록한 제보를 페이지 단위로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제보 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 페이지 요청", content = @Content)
	public ResponseEntity<ApiResponse<PageResponse<ReportListItemView>>> getMyList(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		Long reporterId = CurrentUser.id(jwt);
		try {
			return ResponseEntity.ok(ApiResponse.success(PageResponse.from(reportQueryService.getMyList(reporterId, page, size))));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		}
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "제보 등록", description = "제보 유형(IMAGE/LIVE)에 맞는 본문으로 제보를 접수합니다. 접수 후 자치구 판별·관제센터 라우팅·담당자 배정이 비동기로 진행됩니다.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "제보 유형에 맞는 요청 본문을 선택합니다.",
			required = true,
			content = @Content(
					schema = @Schema(implementation = CreateReportRequest.class),
					examples = {
						@ExampleObject(name = "IMAGE", summary = "이미지 제보 접수", value = ReportExamples.CREATE_IMAGE),
						@ExampleObject(name = "LIVE", summary = "라이브 제보 접수", value = ReportExamples.CREATE_LIVE)
					}
			)
	)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제보 등록 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "제보를 등록할 수 없는 상태", content = @Content)
	public ResponseEntity<ApiResponse<CreateReportResponse>> create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateReportRequest request
	) {
		Long reporterId = CurrentUser.id(jwt);
		try {
			CreateReportResult result = createReportService.create(new CreateReportCommand(
					reporterId, request.reportType(), request.category(), request.subcategory(), request.description(),
					request.latitude(), request.longitude(), request.address()
			));
			return ResponseEntity.created(URI.create("/api/v1/reports/" + result.reportId()))
					.body(ApiResponse.success(CreateReportResponse.from(result)));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
	}
}
