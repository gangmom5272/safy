package com.ojimasam.safy.notification.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.notification.application.UserLocationPresenceService;
import com.ojimasam.safy.notification.presentation.dto.UpdateLocationPresenceRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me/location-presence")
@PreAuthorize("hasRole('CITIZEN')")
public class UserLocationPresenceController {
	private final UserLocationPresenceService service;

	public UserLocationPresenceController(UserLocationPresenceService service) {
		this.service = service;
	}

	@PutMapping
	public ApiResponse<Void> update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateLocationPresenceRequest request) {
		service.update(CurrentUser.id(jwt), request.latitude(), request.longitude());
		return ApiResponse.successWithoutData();
	}

	@DeleteMapping
	public ApiResponse<Void> disable(@AuthenticationPrincipal Jwt jwt) {
		service.disable(CurrentUser.id(jwt));
		return ApiResponse.successWithoutData();
	}
}
