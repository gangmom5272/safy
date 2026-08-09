package com.ojimasam.safy.live.presentation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.live.application.LiveWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.ojimasam.safy.common.api.ApiResponse;

/**
 * 미디어 서버(LiveKit)가 호출하는 웹훅 엔드포인트.
 * 사용자 토큰이 아니라 미디어 서버 API 시크릿으로 서명된 JWT를 검증하므로 인증 예외 경로로 열려 있다.
 */
@RestController
@RequestMapping("/api/v1/live/webhooks")
@Tag(name = "라이브 웹훅", description = "미디어 서버 이벤트 수신(서버 간 호출 전용)")
public class LiveWebhookController {
	private final LiveWebhookService service;

	public LiveWebhookController(LiveWebhookService service) {
		this.service = service;
	}

	@PostMapping(value = "/livekit", consumes = MediaType.ALL_VALUE)
	@Operation(summary = "미디어 서버 웹훅 수신",
			description = "참가자 입퇴장·룸 종료 이벤트를 받아 라이브 세션 상태를 정합화합니다. "
					+ "Authorization 헤더의 서명된 JWT로 검증하며, 일반 사용자 토큰으로는 호출할 수 없습니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "웹훅 처리 완료")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "웹훅 서명 검증 실패", content = @Content)
	public ResponseEntity<Void> livekit(
			@RequestBody String body,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
	) {
		service.handle(body, authorization);
		return ResponseEntity.noContent().build();
	}
}
