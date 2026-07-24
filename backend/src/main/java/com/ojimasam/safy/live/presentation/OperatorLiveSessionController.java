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
import com.ojimasam.safy.live.application.LiveTokenView;
import com.ojimasam.safy.live.application.LiveSessionCursorView;
import com.ojimasam.safy.live.application.OperatorLiveSessionView;
import com.ojimasam.safy.live.domain.LiveSessionStatus;

import java.time.Instant;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/admin/stream-rooms")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class OperatorLiveSessionController {
	private final LiveSessionService service;

	public OperatorLiveSessionController(LiveSessionService service) {
		this.service = service;
	}

	@PostMapping("/{streamRoomId}/tokens")
	public ResponseEntity<ApiResponse<LiveTokenView>> viewerToken(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(service.issueViewerToken(CurrentUser.id(jwt), streamRoomId)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<LiveSessionCursorView>> list(
			@RequestParam(required = false) LiveSessionStatus status,
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size) {
		int boundedSize = Math.max(1, Math.min(size, 100));
		return ResponseEntity.ok(ApiResponse.success(service.getForOperator(status, cursor, boundedSize)));
	}

	@GetMapping("/{streamRoomId}")
	public ResponseEntity<ApiResponse<OperatorLiveSessionView>> detail(@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(service.getForOperator(streamRoomId)));
	}
}
