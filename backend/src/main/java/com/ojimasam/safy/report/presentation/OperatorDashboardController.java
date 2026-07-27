package com.ojimasam.safy.report.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.report.application.OperatorDashboardService;
import com.ojimasam.safy.report.application.OperatorDashboardTileView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/operator/dashboard/reports")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "관제 대시보드", description = "담당자 관제 화면용 제보 타일 목록")
public class OperatorDashboardController {
	private final OperatorDashboardService service;

	public OperatorDashboardController(OperatorDashboardService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "관제 대시보드 제보 타일 조회", description = "담당자 관제 화면에 표시할 제보 타일을 페이지 단위로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대시보드 타일 조회 성공")
	public ApiResponse<PageResponse<OperatorDashboardTileView>> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return ApiResponse.success(PageResponse.from(service.getTiles(CurrentUser.id(jwt), page, size)));
	}
}
