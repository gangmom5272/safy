package com.ojimasam.safy.notification.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.notification.domain.UserLocationPresence;
import com.ojimasam.safy.notification.infrastructure.JpaUserLocationPresenceRepository;
import com.ojimasam.safy.place.application.PlaceLocator;

@Service
public class UserLocationPresenceService {
	private static final Duration RETENTION = Duration.ofHours(24);

	private final JpaUserLocationPresenceRepository repository;
	private final NotificationSettingsService settingsService;
	private final PlaceLocator placeLocator;
	private final Clock clock;

	@Autowired
	public UserLocationPresenceService(JpaUserLocationPresenceRepository repository,
			NotificationSettingsService settingsService, PlaceLocator placeLocator) {
		this(repository, settingsService, placeLocator, Clock.systemUTC());
	}

	UserLocationPresenceService(JpaUserLocationPresenceRepository repository,
			NotificationSettingsService settingsService, PlaceLocator placeLocator, Clock clock) {
		this.repository = repository;
		this.settingsService = settingsService;
		this.placeLocator = placeLocator;
		this.clock = clock;
	}

	@Transactional
	public void update(Long userId, BigDecimal latitude, BigDecimal longitude) {
		if (!settingsService.allowsLocationRequest(userId)) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "위치 기반 제보 요청 알림을 먼저 켜주세요.");
		}
		Long placeId = placeLocator.locate(latitude, longitude).orElse(null);
		UserLocationPresence presence = repository.findById(userId).map(existing -> {
			existing.update(latitude, longitude, placeId, clock.instant());
			return existing;
		}).orElseGet(() -> UserLocationPresence.create(
				userId, latitude, longitude, placeId, clock.instant()));
		repository.save(presence);
	}

	@Transactional
	public void disable(Long userId) {
		repository.deleteById(userId);
	}

	@Scheduled(cron = "${app.web-push.location-presence-cleanup-cron:0 20 * * * *}")
	@Transactional
	public void cleanupExpired() {
		repository.deleteByLastSeenAtBefore(clock.instant().minus(RETENTION));
	}
}
