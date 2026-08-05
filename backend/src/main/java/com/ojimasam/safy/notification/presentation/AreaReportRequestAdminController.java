package com.ojimasam.safy.notification.presentation;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.AreaReportRequestService;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestPlaceView;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestView;
import com.ojimasam.safy.notification.presentation.dto.CreateAreaReportRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/area-report-requests")
@PreAuthorize("hasRole('OPERATOR')")
public class AreaReportRequestAdminController {
	private final AreaReportRequestService service;

	public AreaReportRequestAdminController(AreaReportRequestService service) {
		this.service = service;
	}

	@GetMapping("/places")
	public ApiResponse<List<AreaReportRequestPlaceView>> places() {
		return ApiResponse.success(service.places());
	}

	@PostMapping
	public ApiResponse<AreaReportRequestView> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateAreaReportRequest request) {
		return ApiResponse.success(service.create(CurrentUser.id(jwt), request.placeId(),
				request.title(), request.content(), request.expiresInMinutes()));
	}
}
