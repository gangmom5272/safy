package com.ojimasam.safy.live.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.live.application.LiveSessionService;
import com.ojimasam.safy.live.application.dto.LiveTokenView;
import com.ojimasam.safy.live.application.dto.LiveSessionCursorView;
import com.ojimasam.safy.live.application.dto.OperatorLiveSessionView;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/stream-rooms")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "라이브 관제(담당자)", description = "담당자의 라이브 세션 목록·상세 조회 및 시청 토큰 발급")
public class OperatorLiveSessionController {
	private final LiveSessionService service;

	public OperatorLiveSessionController(LiveSessionService service) {
		this.service = service;
	}

	@PostMapping("/{streamRoomId}/tokens")
	@Operation(summary = "시청 토큰 발급(담당자)", description = "배정받은 담당자가 해당 스트림 룸의 영상을 구독(시청)하기 위한 WebRTC 토큰을 발급합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시청 토큰 발급 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 세션을 시청할 권한이 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스트림 룸을 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<LiveTokenView>> viewerToken(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(service.issueViewerToken(CurrentUser.id(jwt), streamRoomId)));
	}

	@GetMapping
	@Operation(summary = "라이브 세션 목록 조회(담당자)", description = "상태·커서 기반으로 담당자가 볼 수 있는 라이브 세션 목록을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "라이브 세션 목록 조회 성공")
	public ResponseEntity<ApiResponse<LiveSessionCursorView>> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) LiveSessionStatus status,
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size) {
		int boundedSize = Math.max(1, Math.min(size, 100));
		return ResponseEntity.ok(ApiResponse.success(
				service.getForOperator(CurrentUser.id(jwt), status, cursor, boundedSize)));
	}

	@GetMapping("/{streamRoomId}")
	@Operation(summary = "라이브 세션 상세 조회(담당자)", description = "담당자가 특정 라이브 세션의 상세 정보를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "라이브 세션 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스트림 룸을 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<OperatorLiveSessionView>> detail(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(
				service.getForOperator(CurrentUser.id(jwt), streamRoomId)));
	}
}
