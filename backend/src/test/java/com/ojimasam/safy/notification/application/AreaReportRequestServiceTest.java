package com.ojimasam.safy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.notification.domain.AreaReportRequest;
import com.ojimasam.safy.notification.domain.UserLocationPresence;
import com.ojimasam.safy.notification.infrastructure.JpaAreaReportRequestRepository;
import com.ojimasam.safy.notification.infrastructure.JpaUserLocationPresenceRepository;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;

@ExtendWith(MockitoExtension.class)
class AreaReportRequestServiceTest {
	@Mock JpaAreaReportRequestRepository requestRepository;
	@Mock JpaUserLocationPresenceRepository presenceRepository;
	@Mock PlaceRepository placeRepository;
	@Mock UserRepository userRepository;
	@Mock NotificationSettingsService settingsService;
	@Mock RealtimeEventService realtimeEventService;

	@Test
	void sendsOnlyToEligibleFreshUsersInTheSelectedPlace() {
		Instant now = Instant.parse("2026-08-04T03:00:00Z");
		Place place = Place.create("place-1", "서울광장", PlaceCategory.TOURIST_ZONE,
				new BigDecimal("37.5665"), new BigDecimal("126.9780"), "test");
		ReflectionTestUtils.setField(place, "id", 1L);
		User eligibleUser = User.citizen("citizen");
		ReflectionTestUtils.setField(eligibleUser, "id", 10L);
		User optedOutUser = User.citizen("opted-out");
		ReflectionTestUtils.setField(optedOutUser, "id", 11L);

		when(placeRepository.findById(1L)).thenReturn(Optional.of(place));
		when(requestRepository.save(org.mockito.ArgumentMatchers.any(AreaReportRequest.class)))
				.thenAnswer(invocation -> {
					AreaReportRequest request = invocation.getArgument(0);
					ReflectionTestUtils.setField(request, "id", 50L);
					return request;
				});
		when(presenceRepository.findAllByPlaceIdAndLastLocationAtGreaterThanEqualAndLastSeenAtGreaterThanEqual(
				1L, now.minusSeconds(600), now.minusSeconds(300)))
				.thenReturn(List.of(
						UserLocationPresence.create(10L, new BigDecimal("37.5665"), new BigDecimal("126.9780"), 1L, now),
						UserLocationPresence.create(11L, new BigDecimal("37.5666"), new BigDecimal("126.9781"), 1L, now)));
		when(userRepository.findById(10L)).thenReturn(Optional.of(eligibleUser));
		when(userRepository.findById(11L)).thenReturn(Optional.of(optedOutUser));
		when(settingsService.allowsLocationRequest(10L)).thenReturn(true);
		when(settingsService.allowsLocationRequest(11L)).thenReturn(false);

		AreaReportRequestService service = new AreaReportRequestService(
				requestRepository, presenceRepository, placeRepository, userRepository,
				settingsService, realtimeEventService, Clock.fixed(now, ZoneOffset.UTC));

		var result = service.create(99L, 1L, "현장 제보 요청", "현재 상황을 알려주세요.", 30);

		assertThat(result.recipientCount()).isEqualTo(1);
		verify(realtimeEventService).record(10L, RealtimeEventType.AREA_REPORT_REQUEST, null, "50", null);
	}
}
