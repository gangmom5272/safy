package com.ojimasam.safy.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestPlaceView;
import com.ojimasam.safy.notification.application.dto.AreaReportRequestView;
import com.ojimasam.safy.notification.domain.AreaReportRequest;
import com.ojimasam.safy.notification.infrastructure.JpaAreaReportRequestRepository;
import com.ojimasam.safy.notification.infrastructure.JpaUserLocationPresenceRepository;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.type.UserRole;

@Service
public class AreaReportRequestService {
	private static final Duration LOCATION_FRESHNESS = Duration.ofMinutes(10);
	private static final Duration ACTIVITY_FRESHNESS = Duration.ofMinutes(5);

	private final JpaAreaReportRequestRepository requestRepository;
	private final JpaUserLocationPresenceRepository presenceRepository;
	private final PlaceRepository placeRepository;
	private final UserRepository userRepository;
	private final NotificationSettingsService settingsService;
	private final RealtimeEventService realtimeEventService;
	private final Clock clock;

	@Autowired
	public AreaReportRequestService(JpaAreaReportRequestRepository requestRepository,
			JpaUserLocationPresenceRepository presenceRepository, PlaceRepository placeRepository,
			UserRepository userRepository, NotificationSettingsService settingsService,
			RealtimeEventService realtimeEventService) {
		this(requestRepository, presenceRepository, placeRepository, userRepository,
				settingsService, realtimeEventService, Clock.systemUTC());
	}

	AreaReportRequestService(JpaAreaReportRequestRepository requestRepository,
			JpaUserLocationPresenceRepository presenceRepository, PlaceRepository placeRepository,
			UserRepository userRepository, NotificationSettingsService settingsService,
			RealtimeEventService realtimeEventService, Clock clock) {
		this.requestRepository = requestRepository;
		this.presenceRepository = presenceRepository;
		this.placeRepository = placeRepository;
		this.userRepository = userRepository;
		this.settingsService = settingsService;
		this.realtimeEventService = realtimeEventService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<AreaReportRequestPlaceView> places() {
		return placeRepository.findAllByActiveTrueOrderByNameAsc().stream()
				.filter(place -> place.getCenterLatitude() != null && place.getCenterLongitude() != null)
				.map(AreaReportRequestPlaceView::from)
				.toList();
	}

	@Transactional
	public AreaReportRequestView create(Long requesterUserId, Long placeId, String title,
			String content, int expiresInMinutes) {
		if (expiresInMinutes < 5 || expiresInMinutes > 120) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "요청 유효시간은 5~120분이어야 합니다.");
		}
		Place place = placeRepository.findById(placeId)
				.filter(Place::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		Instant now = clock.instant();
		AreaReportRequest request = requestRepository.save(AreaReportRequest.create(
				requesterUserId, place.getId(), place.getName(), title, content,
				now, now.plus(Duration.ofMinutes(expiresInMinutes))));

		List<Long> recipients = presenceRepository
				.findAllByPlaceIdAndLastLocationAtGreaterThanEqualAndLastSeenAtGreaterThanEqual(
						placeId, now.minus(LOCATION_FRESHNESS), now.minus(ACTIVITY_FRESHNESS))
				.stream()
				.map(presence -> presence.getUserId())
				.distinct()
				.filter(this::eligible)
				.toList();
		for (Long recipientId : recipients) {
			realtimeEventService.record(recipientId, RealtimeEventType.AREA_REPORT_REQUEST,
					null, request.getId().toString(), null);
		}
		request.recordRecipients(recipients.size());
		return AreaReportRequestView.from(request, place);
	}

	@Transactional(readOnly = true)
	public AreaReportRequestView get(Long requestId) {
		AreaReportRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		Place place = placeRepository.findById(request.getPlaceId())
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return AreaReportRequestView.from(request, place);
	}

	private boolean eligible(Long userId) {
		User user = userRepository.findById(userId).orElse(null);
		return user != null && user.isActive() && user.getRole() == UserRole.CITIZEN
				&& settingsService.allowsLocationRequest(userId);
	}
}
