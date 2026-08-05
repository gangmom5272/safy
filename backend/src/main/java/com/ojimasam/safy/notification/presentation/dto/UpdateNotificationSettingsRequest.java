package com.ojimasam.safy.notification.presentation.dto;

public record UpdateNotificationSettingsRequest(
		boolean enabled,
		boolean reportEnabled,
		boolean safetyEnabled,
		boolean systemEnabled,
		boolean savedLocationEnabled,
		boolean locationRequestEnabled
) {
}
