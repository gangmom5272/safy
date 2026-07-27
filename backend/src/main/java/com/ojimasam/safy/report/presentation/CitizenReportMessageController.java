package com.ojimasam.safy.report.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.report.application.ReportMessageResponse;
import com.ojimasam.safy.report.application.ReportMessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports/{reportId}/messages")
@PreAuthorize("hasRole('CITIZEN')")
public class CitizenReportMessageController {
	private final ReportMessageService service;

	public CitizenReportMessageController(ReportMessageService service) {
		this.service = service;
	}

	@PostMapping
	public ApiResponse<ReportMessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId,
			@Valid @RequestBody SendReportMessageRequest request) {
		return ApiResponse.success(service.sendFromCitizen(
				CurrentUser.id(jwt), reportId, request.content()));
	}

	@GetMapping
	public ApiResponse<PageResponse<ReportMessageResponse>> list(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId, Pageable pageable) {
		return ApiResponse.success(PageResponse.from(
				service.listForCitizen(CurrentUser.id(jwt), reportId, pageable)));
	}
}
