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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/web-push")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "웹 푸시", description = "브라우저 Web Push 구독 관리")
public class WebPushController {
	private final WebPushSubscriptionService service;

	public WebPushController(WebPushSubscriptionService service) {
		this.service = service;
	}

	@GetMapping("/public-key")
	@Operation(summary = "Web Push 공개키 조회",
			description = "구독 등록에 필요한 VAPID 공개키를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "공개키 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<Map<String, String>> publicKey() {
		return ApiResponse.success(Map.of("publicKey", service.publicKey()));
	}

	@PostMapping("/subscriptions")
	@Operation(summary = "Web Push 구독 등록",
			description = "브라우저의 푸시 구독 정보를 등록합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구독 등록 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<Void> register(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody RegisterWebPushRequest request) {
		service.register(CurrentUser.id(jwt), request.endpoint(), request.keys().p256dh(),
				request.keys().auth(), request.userAgent());
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping("/subscriptions")
	@Operation(summary = "Web Push 구독 해제",
			description = "등록된 브라우저 푸시 구독을 해제합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구독 해제 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<Void> disable(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DisableWebPushRequest request) {
		service.disable(CurrentUser.id(jwt), request.endpoint());
		return ApiResponse.successWithoutData();
	}
}
