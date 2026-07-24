package com.ojimasam.safy.live.presentation;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.live.application.CreateLiveSessionCommand;
import com.ojimasam.safy.live.application.LiveSessionService;
import com.ojimasam.safy.live.application.LiveSessionView;
import com.ojimasam.safy.live.application.LiveTokenView;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class LiveSessionController {
	private final LiveSessionService service;

	public LiveSessionController(LiveSessionService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/reports/live")
	public ResponseEntity<ApiResponse<LiveSessionView>> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateLiveSessionRequest request) {
		LiveSessionView result = service.create(new CreateLiveSessionCommand(
				CurrentUser.id(jwt), request.description(), request.latitude(), request.longitude(), request.address()));
		return ResponseEntity.created(URI.create("/api/v1/stream-rooms/" + result.streamRoomId()))
				.body(ApiResponse.success(result));
	}

	@PostMapping("/api/v1/stream-rooms/{streamRoomId}/tokens")
	public ResponseEntity<ApiResponse<LiveTokenView>> reporterToken(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId, @Valid @RequestBody StreamTokenRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				service.issueReporterToken(CurrentUser.id(jwt), streamRoomId)));
	}

	@GetMapping("/api/v1/stream-rooms/{streamRoomId}")
	public ResponseEntity<ApiResponse<LiveSessionView>> status(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(
				service.getForReporter(CurrentUser.id(jwt), streamRoomId)));
	}

	@PatchMapping("/api/v1/stream-rooms/{streamRoomId}/end")
	public ResponseEntity<Void> end(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		service.end(CurrentUser.id(jwt), streamRoomId);
		return ResponseEntity.noContent().build();
	}

}
