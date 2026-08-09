package com.ojimasam.safy.admin.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.admin.application.OperatorPresenceService;
import com.ojimasam.safy.admin.application.dto.OperatorPresenceResponse;
import com.ojimasam.safy.admin.presentation.dto.UpdateOperatorPresenceRequest;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/operator/presence")
@PreAuthorize("hasRole('OPERATOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "담당자 근무 상태", description = "담당자의 근무중·자리비움·오프라인 상태 조회 및 변경")
public class OperatorPresenceController {
	private final OperatorPresenceService presenceService;

	public OperatorPresenceController(OperatorPresenceService presenceService) {
		this.presenceService = presenceService;
	}

	@GetMapping
	@Operation(summary = "내 근무 상태 조회", description = "접속 이력이 없으면 오프라인으로 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "근무 상태 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<OperatorPresenceResponse> getMine(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.success(presenceService.getMine(CurrentUser.id(jwt)));
	}

	@PatchMapping
	@Operation(summary = "내 근무 상태 변경",
			description = "근무중·자리비움·오프라인으로 전환합니다. 오프라인으로 전환하면 "
					+ "아직 확인하지 않은(ASSIGNED) 배정이 즉시 회수되어 다른 담당자에게 재배정되고, "
					+ "화면 연결까지 끊긴 채 유예가 지나면 처리중(IN_PROGRESS) 배정도 회수됩니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "근무 상태 변경 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<OperatorPresenceResponse> change(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateOperatorPresenceRequest request
	) {
		return ApiResponse.success(presenceService.changeStatus(CurrentUser.id(jwt), request.status()));
	}
}
