package com.ojimasam.safy.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.notification.application.dto.NotificationSettingsView;
import com.ojimasam.safy.notification.domain.NotificationSettings;
import com.ojimasam.safy.notification.domain.type.NotificationType;
import com.ojimasam.safy.notification.infrastructure.JpaNotificationSettingsRepository;

@Service
public class NotificationSettingsService {
	private final JpaNotificationSettingsRepository repository;

	public NotificationSettingsService(JpaNotificationSettingsRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public NotificationSettingsView get(Long userId) {
		return NotificationSettingsView.from(getOrCreate(userId));
	}

	@Transactional
	public NotificationSettingsView update(Long userId, boolean enabled, boolean reportEnabled,
			boolean safetyEnabled, boolean systemEnabled, boolean savedLocationEnabled,
			boolean locationRequestEnabled) {
		NotificationSettings settings = getOrCreate(userId);
		settings.update(enabled, reportEnabled, safetyEnabled, systemEnabled,
				savedLocationEnabled, locationRequestEnabled);
		return NotificationSettingsView.from(settings);
	}

	@Transactional(readOnly = true)
	public boolean allows(Long userId, NotificationType type) {
		return repository.findById(userId).map(settings -> settings.allows(type)).orElse(false);
	}

	@Transactional(readOnly = true)
	public boolean allowsLocationRequest(Long userId) {
		return repository.findById(userId)
				.map(settings -> settings.isEnabled() && settings.isSafetyEnabled()
						&& settings.isLocationRequestEnabled())
				.orElse(false);
	}

	private NotificationSettings getOrCreate(Long userId) {
		return repository.findById(userId).orElseGet(() -> repository.save(NotificationSettings.defaults(userId)));
	}
}
