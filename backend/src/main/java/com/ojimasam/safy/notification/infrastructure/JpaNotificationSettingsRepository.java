package com.ojimasam.safy.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.notification.domain.NotificationSettings;

public interface JpaNotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
}
