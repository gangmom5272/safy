package com.ojimasam.safy.notification.domain;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.notification.domain.type.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_settings")
public class NotificationSettings extends BaseEntity {
	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "report_enabled", nullable = false)
	private boolean reportEnabled;

	@Column(name = "safety_enabled", nullable = false)
	private boolean safetyEnabled;

	@Column(name = "system_enabled", nullable = false)
	private boolean systemEnabled;

	@Column(name = "saved_location_enabled", nullable = false)
	private boolean savedLocationEnabled;

	@Column(name = "location_request_enabled", nullable = false)
	private boolean locationRequestEnabled;

	protected NotificationSettings() {
	}

	private NotificationSettings(Long userId) {
		this.userId = userId;
		this.enabled = false;
		this.reportEnabled = true;
		this.safetyEnabled = true;
		this.systemEnabled = true;
		this.savedLocationEnabled = false;
		this.locationRequestEnabled = false;
	}

	public static NotificationSettings defaults(Long userId) {
		if (userId == null) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
		return new NotificationSettings(userId);
	}

	public void update(boolean enabled, boolean reportEnabled, boolean safetyEnabled,
			boolean systemEnabled, boolean savedLocationEnabled, boolean locationRequestEnabled) {
		this.enabled = enabled;
		this.reportEnabled = reportEnabled;
		this.safetyEnabled = safetyEnabled;
		this.systemEnabled = systemEnabled;
		this.savedLocationEnabled = savedLocationEnabled;
		this.locationRequestEnabled = locationRequestEnabled;
	}

	public boolean allows(NotificationType type) {
		if (!enabled) return false;
		return switch (type) {
			case REPORT -> reportEnabled;
			case SAFETY -> safetyEnabled;
			case SYSTEM -> systemEnabled;
		};
	}

	public Long getUserId() { return userId; }
	public boolean isEnabled() { return enabled; }
	public boolean isReportEnabled() { return reportEnabled; }
	public boolean isSafetyEnabled() { return safetyEnabled; }
	public boolean isSystemEnabled() { return systemEnabled; }
	public boolean isSavedLocationEnabled() { return savedLocationEnabled; }
	public boolean isLocationRequestEnabled() { return locationRequestEnabled; }
}
