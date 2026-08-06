package com.ojimasam.safy.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.infrastructure.JpaNotificationRepository;
import com.ojimasam.safy.notification.infrastructure.JpaAreaReportRequestRepository;
import com.ojimasam.safy.realtime.application.RealtimeEventRecorded;
import com.ojimasam.safy.realtime.application.dto.RealtimeEventView;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
	@Mock JpaNotificationRepository repository;
	@Mock JpaAreaReportRequestRepository areaReportRequestRepository;
	@Mock NotificationPushDeliveryService pushDeliveryService;

	@Test
	void createsReportNotificationFromClosedRealtimeEvent() {
		when(repository.existsBySourceEventId(101L)).thenReturn(false);
		when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
			Notification notification = invocation.getArgument(0);
			ReflectionTestUtils.setField(notification, "id", 501L);
			return notification;
		});
		NotificationService service = new NotificationService(
				repository, areaReportRequestRepository, pushDeliveryService);

		service.createFromRealtime(recorded(RealtimeEventType.REPORT_CLOSED));

		ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
		verify(repository).save(notification.capture());
		assertEquals(30L, notification.getValue().getRecipientUserId());
		assertEquals(10L, notification.getValue().getReportId());
		assertEquals("상황이 종료되었습니다", notification.getValue().getTitle());
		verify(pushDeliveryService).enqueue(notification.getValue());
	}

	@Test
	void doesNotCreateNotificationForChatSignal() {
		NotificationService service = new NotificationService(
				repository, areaReportRequestRepository, pushDeliveryService);

		service.createFromRealtime(recorded(RealtimeEventType.REPORT_MESSAGE));

		verify(repository, never()).save(any());
		verify(pushDeliveryService, never()).enqueue(any());
	}

	@Test
	void returnsUnreadCountForCurrentUser() {
		when(repository.countByRecipientUserIdAndReadAtIsNull(30L)).thenReturn(4L);
		NotificationService service = new NotificationService(
				repository, areaReportRequestRepository, pushDeliveryService);

		assertEquals(4L, service.unreadCount(30L));
	}

	private RealtimeEventRecorded recorded(RealtimeEventType type) {
		return new RealtimeEventRecorded(30L,
				new RealtimeEventView(101L, type, 10L, null, null, Instant.parse("2026-08-04T00:00:00Z")));
	}
}
