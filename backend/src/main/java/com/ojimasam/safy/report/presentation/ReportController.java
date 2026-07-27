package com.ojimasam.safy.report.presentation;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
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
import com.ojimasam.safy.report.presentation.docs.ReportApiDocs;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CITIZEN')")
public class ReportController implements ReportApiDocs {
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
	@Override
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
	@Override
	public ResponseEntity<ApiResponse<ReportDetailView>> getMine(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId
	) {
		Long reporterId = CurrentUser.id(jwt);
		return ResponseEntity.ok(ApiResponse.success(reportQueryService.getMine(reporterId, reportId)));
	}

	@GetMapping("/{reportId}/status-history")
	@Override
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
	@Override
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
	@Override
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
