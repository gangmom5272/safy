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
import com.ojimasam.safy.report.application.dto.OperatorDashboardTileView;
import com.ojimasam.safy.report.application.dto.OperatorQueueItemView;

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
	@Operation(summary = "관제 대시보드 제보 타일 조회",
			description = "담당자 관제 화면에 표시할 제보 타일을 페이지 단위로 조회합니다.\n\n"
					+ "시각 필드가 둘입니다. **reportedAt** 은 시민이 제보한 시각이고, "
					+ "**assignedAt** 은 이 담당자에게 배정된 시각입니다. 재배정되면 배정 시각만 뒤로 밀리므로 "
					+ "'최신 제보순' 정렬과 '제보 시간' 표시에는 reportedAt 을 써야 합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대시보드 타일 조회 성공")
	public ApiResponse<PageResponse<OperatorDashboardTileView>> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return ApiResponse.success(PageResponse.from(service.getTiles(CurrentUser.id(jwt), page, size)));
	}

	@GetMapping("/queue")
	@PreAuthorize("hasRole('OPERATOR')")
	@Operation(summary = "센터 공용 큐 조회",
			description = "내 관제센터로 라우팅됐지만 아직 담당자가 없는 제보를 오래된 순으로 조회합니다. "
					+ "자동 배정이 실패했거나 반납·회수된 건이 쌓이는 안전망으로, 평소에는 비어 있는 게 정상입니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공용 큐 조회 성공")
	public ApiResponse<PageResponse<OperatorQueueItemView>> queue(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return ApiResponse.success(PageResponse.from(service.getQueue(CurrentUser.id(jwt), page, size)));
	}
}
