package com.ojimasam.safy.notification.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.NotificationSettingsService;
import com.ojimasam.safy.notification.application.UserLocationPresenceService;
import com.ojimasam.safy.notification.application.dto.NotificationSettingsView;
import com.ojimasam.safy.notification.presentation.dto.UpdateNotificationSettingsRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users/me/notification-settings")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "알림 설정", description = "현재 사용자의 Web Push 알림 설정")
public class NotificationSettingsController {
	private final NotificationSettingsService service;
	private final UserLocationPresenceService locationPresenceService;

	public NotificationSettingsController(NotificationSettingsService service,
			UserLocationPresenceService locationPresenceService) {
		this.service = service;
		this.locationPresenceService = locationPresenceService;
	}

	@GetMapping
	public ApiResponse<NotificationSettingsView> get(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.success(service.get(CurrentUser.id(jwt)));
	}

	@PatchMapping
	public ApiResponse<NotificationSettingsView> update(@AuthenticationPrincipal Jwt jwt,
			@RequestBody UpdateNotificationSettingsRequest request) {
		Long userId = CurrentUser.id(jwt);
		NotificationSettingsView updated = service.update(
				userId, request.enabled(), request.reportEnabled(), request.safetyEnabled(),
				request.systemEnabled(), request.savedLocationEnabled(), request.locationRequestEnabled()
		);
		if (!updated.locationRequestEnabled()) locationPresenceService.disable(userId);
		return ApiResponse.success(updated);
	}
}
