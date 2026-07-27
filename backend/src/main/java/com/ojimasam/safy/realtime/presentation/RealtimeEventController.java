package com.ojimasam.safy.realtime.presentation;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.realtime.application.RealtimeEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/realtime/events")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "실시간 이벤트(SSE)", description = "담당자·시민에게 전달되는 실시간 알림 이벤트 구독")
public class RealtimeEventController {
	private final RealtimeEventService service;

	public RealtimeEventController(RealtimeEventService service) {
		this.service = service;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "실시간 이벤트 구독(SSE)", description = "text/event-stream으로 실시간 알림을 구독합니다. Last-Event-ID 헤더로 놓친 이벤트부터 재수신할 수 있습니다.")
	@Parameter(name = "Last-Event-ID", in = ParameterIn.HEADER, description = "마지막으로 수신한 이벤트 ID (재연결 시 사용)")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이벤트 스트림 연결 성공")
	public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId) {
		return service.subscribe(CurrentUser.id(jwt), lastEventId);
	}
}
