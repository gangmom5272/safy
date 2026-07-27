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
import com.ojimasam.safy.realtime.application.RealtimeEventService;

@RestController
@RequestMapping("/api/v1/realtime/events")
public class RealtimeEventController {
	private final RealtimeEventService service;

	public RealtimeEventController(RealtimeEventService service) {
		this.service = service;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId) {
		return service.subscribe(CurrentUser.id(jwt), lastEventId);
	}
}
