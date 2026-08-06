package com.ojimasam.safy.notification.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.AreaReportRequestService;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/area-report-requests")
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "지역 제보 요청(시민)", description = "담당자가 보낸 지역 제보 요청 알림의 상세 조회")
public class AreaReportRequestController {
	private final AreaReportRequestService service;

	public AreaReportRequestController(AreaReportRequestService service) {
		this.service = service;
	}

	@GetMapping("/{requestId}")
	@Operation(summary = "지역 제보 요청 상세 조회",
			description = "지역 제보 요청 알림을 받은 시민이 요청 상세 내용을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역 제보 요청 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "시민 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "요청을 찾을 수 없음", content = @Content)
	public ApiResponse<AreaReportRequestView> get(@PathVariable Long requestId) {
		return ApiResponse.success(service.get(requestId));
	}
}
