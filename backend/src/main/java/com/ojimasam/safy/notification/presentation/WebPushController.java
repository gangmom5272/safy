package com.ojimasam.safy.notification.presentation;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.WebPushSubscriptionService;
import com.ojimasam.safy.notification.presentation.dto.DisableWebPushRequest;
import com.ojimasam.safy.notification.presentation.dto.RegisterWebPushRequest;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/web-push")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Web Push", description = "브라우저 Web Push 구독 관리")
public class WebPushController {
	private final WebPushSubscriptionService service;

	public WebPushController(WebPushSubscriptionService service) {
		this.service = service;
	}

	@GetMapping("/public-key")
	public ApiResponse<Map<String, String>> publicKey() {
		return ApiResponse.success(Map.of("publicKey", service.publicKey()));
	}

	@PostMapping("/subscriptions")
	public ApiResponse<Void> register(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RegisterWebPushRequest request) {
		service.register(CurrentUser.id(jwt), request.endpoint(), request.keys().p256dh(),
				request.keys().auth(), request.userAgent());
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/subscriptions")
	public ApiResponse<Void> disable(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DisableWebPushRequest request) {
		service.disable(CurrentUser.id(jwt), request.endpoint());
		return ApiResponse.successWithoutData();
	}
}
