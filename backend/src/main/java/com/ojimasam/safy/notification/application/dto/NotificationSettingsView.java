package com.ojimasam.safy.notification.application.dto;

import com.ojimasam.safy.notification.domain.NotificationSettings;

public record NotificationSettingsView(
		boolean enabled,
		boolean reportEnabled,
		boolean safetyEnabled,
		boolean systemEnabled,
		boolean savedLocationEnabled,
		boolean locationRequestEnabled
) {
	public static NotificationSettingsView from(NotificationSettings settings) {
		return new NotificationSettingsView(
				settings.isEnabled(), settings.isReportEnabled(), settings.isSafetyEnabled(),
				settings.isSystemEnabled(), settings.isSavedLocationEnabled(),
				settings.isLocationRequestEnabled()
		);
	}
}
