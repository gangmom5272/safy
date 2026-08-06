package com.ojimasam.safy.notification.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.notification.application.NotificationService;
import com.ojimasam.safy.notification.application.dto.NotificationView;
import com.ojimasam.safy.notification.domain.type.NotificationType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "알림", description = "현재 사용자의 알림 조회와 읽음 처리")
public class NotificationController {
	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "알림 목록 조회", description = "현재 사용자의 알림을 유형별로 필터링하여 페이지 단위로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<PageResponse<NotificationView>> findAll(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) NotificationType type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size) {
		return ApiResponse.success(PageResponse.from(service.findAll(CurrentUser.id(jwt), type, page, size)));
	}

	@GetMapping("/unread-count")
	@Operation(summary = "읽지 않은 알림 수 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽지 않은 알림 수 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.success(service.unreadCount(CurrentUser.id(jwt)));
	}

	@PatchMapping("/{notificationId}/read")
	@Operation(summary = "알림 읽음 처리")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 읽음 처리 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음", content = @Content)
	public ApiResponse<Void> markAsRead(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long notificationId) {
		service.markAsRead(CurrentUser.id(jwt), notificationId);
		return ApiResponse.successWithoutData();
	}

	@PatchMapping("/read-all")
	@Operation(summary = "전체 알림 읽음 처리")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 알림 읽음 처리 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
		service.markAllAsRead(CurrentUser.id(jwt));
		return ApiResponse.successWithoutData();
	}
}
