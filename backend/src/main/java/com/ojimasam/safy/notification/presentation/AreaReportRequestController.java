package com.ojimasam.safy.notification.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.AreaReportRequestService;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestView;

@RestController
@RequestMapping("/api/v1/area-report-requests")
@PreAuthorize("hasRole('CITIZEN')")
public class AreaReportRequestController {
	private final AreaReportRequestService service;

	public AreaReportRequestController(AreaReportRequestService service) {
		this.service = service;
	}

	@GetMapping("/{requestId}")
	public ApiResponse<AreaReportRequestView> get(@PathVariable Long requestId) {
		return ApiResponse.success(service.get(requestId));
	}
}
