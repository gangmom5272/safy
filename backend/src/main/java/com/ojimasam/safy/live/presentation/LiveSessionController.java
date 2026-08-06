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
import com.ojimasam.safy.live.application.dto.CreateLiveSessionCommand;
import com.ojimasam.safy.live.application.LiveSessionService;
import com.ojimasam.safy.live.application.dto.LiveSessionView;
import com.ojimasam.safy.live.application.dto.LiveTokenView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.ojimasam.safy.live.presentation.dto.StreamTokenRequest;

@RestController
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "라이브 제보(시민)", description = "WebRTC 라이브 제보 세션 생성 및 송출 토큰 발급")
public class LiveSessionController {
	private final LiveSessionService service;

	public LiveSessionController(LiveSessionService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/reports/{reportId}/live-session")
	@Operation(summary = "라이브 세션 생성", description = "LIVE 제보에 대한 WebRTC 스트림 룸을 생성합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "라이브 세션 생성 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "라이브 세션을 생성할 수 없는 상태", content = @Content)
	public ResponseEntity<ApiResponse<LiveSessionView>> create(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId) {
		LiveSessionView result = service.create(new CreateLiveSessionCommand(CurrentUser.id(jwt), reportId));
		return ResponseEntity.created(URI.create("/api/v1/stream-rooms/" + result.streamRoomId()))
				.body(ApiResponse.success(result));
	}

	@PostMapping("/api/v1/stream-rooms/{streamRoomId}/tokens")
	@Operation(summary = "송출 토큰 발급(제보자)", description = "제보자가 해당 스트림 룸으로 영상을 송출하기 위한 WebRTC 토큰을 발급합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "송출 토큰 발급 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "해당 스트림 룸의 제보자가 아님", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스트림 룸을 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<LiveTokenView>> reporterToken(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId, @Valid @RequestBody StreamTokenRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				service.issueReporterToken(CurrentUser.id(jwt), streamRoomId)));
	}

	@GetMapping("/api/v1/stream-rooms/{streamRoomId}")
	@Operation(summary = "라이브 세션 상태 조회(제보자)", description = "제보자가 본인 스트림 룸의 상태를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "라이브 세션 상태 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스트림 룸을 찾을 수 없음", content = @Content)
	public ResponseEntity<ApiResponse<LiveSessionView>> status(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		return ResponseEntity.ok(ApiResponse.success(
				service.getForReporter(CurrentUser.id(jwt), streamRoomId)));
	}

	@PatchMapping("/api/v1/stream-rooms/{streamRoomId}/end")
	@Operation(summary = "라이브 세션 종료(제보자)", description = "제보자가 본인 스트림 룸의 송출을 종료합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "라이브 세션 종료 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스트림 룸을 찾을 수 없음", content = @Content)
	public ResponseEntity<Void> end(@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID streamRoomId) {
		service.end(CurrentUser.id(jwt), streamRoomId);
		return ResponseEntity.noContent().build();
	}

}
