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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/area-report-requests")
@PreAuthorize("hasRole('OPERATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "지역 제보 요청(담당자)", description = "담당자의 지역 제보 요청 생성 및 요청 대상 장소 조회")
public class AreaReportRequestAdminController {
	private final AreaReportRequestService service;

	public AreaReportRequestAdminController(AreaReportRequestService service) {
		this.service = service;
	}

	@GetMapping("/places")
	@Operation(summary = "지역 제보 요청 대상 장소 조회",
			description = "위치 정보가 있는 활성 장소를 이름순으로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대상 장소 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "담당자 권한 필요", content = @Content)
	public ApiResponse<List<AreaReportRequestPlaceView>> places() {
		return ApiResponse.success(service.places());
	}

	@PostMapping
	@Operation(summary = "지역 제보 요청 생성",
			description = "선택한 장소 인근에서 위치를 공유 중인 시민에게 지역 제보 요청 알림을 보냅니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 생성 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "담당자 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content)
	public ApiResponse<AreaReportRequestView> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateAreaReportRequest request) {
		return ApiResponse.success(service.create(CurrentUser.id(jwt), request.placeId(),
				request.title(), request.content(), request.expiresInMinutes()));
	}
}
