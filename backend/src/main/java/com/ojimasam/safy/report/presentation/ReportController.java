package com.ojimasam.safy.report.presentation;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.report.application.CreateImageReportCommand;
import com.ojimasam.safy.report.application.CreateImageReportService;
import com.ojimasam.safy.report.application.CreateReportResult;
import com.ojimasam.safy.report.application.CreateTextReportCommand;
import com.ojimasam.safy.report.application.CreateTextReportService;
import com.ojimasam.safy.report.application.ReportDetailView;
import com.ojimasam.safy.report.application.ReportListItemView;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.CancelReportService;
import com.ojimasam.safy.report.presentation.docs.ReportApiDocs;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('CITIZEN')")
public class ReportController implements ReportApiDocs {
	private final CreateTextReportService createTextReportService;
	private final CreateImageReportService createImageReportService;
	private final ReportQueryService reportQueryService;
	private final CancelReportService cancelReportService;

	public ReportController(CreateTextReportService createTextReportService,
			CreateImageReportService createImageReportService, ReportQueryService reportQueryService,
			CancelReportService cancelReportService) {
		this.createTextReportService = createTextReportService;
		this.createImageReportService = createImageReportService;
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
			CreateReportResult result = switch (request.reportType()) {
				case TEXT -> createText(reporterId, request);
				case IMAGE -> createImage(reporterId, request);
				case LIVE -> throw new IllegalArgumentException("LIVE 제보는 아직 지원하지 않습니다.");
			};
			return ResponseEntity.created(URI.create("/api/v1/reports/" + result.reportId()))
					.body(ApiResponse.success(CreateReportResponse.from(result)));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
	}

	private CreateReportResult createText(Long reporterId, CreateReportRequest request) {
		if (request.mediaAssetIds() != null && !request.mediaAssetIds().isEmpty()) {
			throw new IllegalArgumentException("TEXT 제보에는 이미지를 첨부할 수 없습니다.");
		}
		return createTextReportService.create(new CreateTextReportCommand(
				reporterId, request.description(), request.latitude(), request.longitude(), request.address()
		));
	}

	private CreateReportResult createImage(Long reporterId, CreateReportRequest request) {
		return createImageReportService.create(new CreateImageReportCommand(
				reporterId, request.description(), request.latitude(), request.longitude(),
				request.address(), request.mediaAssetIds() == null ? List.of() : request.mediaAssetIds()
		));
	}
}
