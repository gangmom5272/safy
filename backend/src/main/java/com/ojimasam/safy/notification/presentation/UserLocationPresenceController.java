package com.ojimasam.safy.notification.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.UserLocationPresenceService;
import com.ojimasam.safy.notification.presentation.dto.UpdateLocationPresenceRequest;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users/me/location-presence")
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "위치 공유 상태", description = "지역 제보 요청 수신 대상 선정을 위한 시민 위치 공유 등록·해제")
public class UserLocationPresenceController {
	private final UserLocationPresenceService service;

	public UserLocationPresenceController(UserLocationPresenceService service) {
		this.service = service;
	}

	@PutMapping
	@Operation(summary = "위치 공유 등록",
			description = "지역 제보 요청 알림을 받기 위해 현재 위치를 공유합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "위치 공유 등록 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "시민 권한 필요", content = @Content)
	public ApiResponse<Void> update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateLocationPresenceRequest request) {
		service.update(CurrentUser.id(jwt), request.latitude(), request.longitude());
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping
	@Operation(summary = "위치 공유 해제",
			description = "위치 공유를 해제하여 지역 제보 요청 알림 대상에서 제외합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "위치 공유 해제 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "시민 권한 필요", content = @Content)
	public ApiResponse<Void> disable(@AuthenticationPrincipal Jwt jwt) {
		service.disable(CurrentUser.id(jwt));
		return ApiResponse.successWithoutData();
	}
}
