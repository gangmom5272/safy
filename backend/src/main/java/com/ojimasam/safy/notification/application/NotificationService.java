package com.ojimasam.safy.notification.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.notification.application.dto.NotificationView;
import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.AreaReportRequest;
import com.ojimasam.safy.notification.domain.type.NotificationType;
import com.ojimasam.safy.notification.infrastructure.JpaNotificationRepository;
import com.ojimasam.safy.notification.infrastructure.JpaAreaReportRequestRepository;
import com.ojimasam.safy.realtime.application.RealtimeEventRecorded;
import com.ojimasam.safy.realtime.application.dto.RealtimeEventView;

@Service
public class NotificationService {
	private static final int MAX_PAGE_SIZE = 100;

	private final JpaNotificationRepository repository;
	private final JpaAreaReportRequestRepository areaReportRequestRepository;
	private final NotificationPushDeliveryService pushDeliveryService;
	private final Clock clock;

	@Autowired
	public NotificationService(JpaNotificationRepository repository,
			JpaAreaReportRequestRepository areaReportRequestRepository,
			NotificationPushDeliveryService pushDeliveryService) {
		this(repository, areaReportRequestRepository, pushDeliveryService, Clock.systemUTC());
	}

	NotificationService(JpaNotificationRepository repository,
			JpaAreaReportRequestRepository areaReportRequestRepository,
			NotificationPushDeliveryService pushDeliveryService, Clock clock) {
		this.repository = repository;
		this.areaReportRequestRepository = areaReportRequestRepository;
		this.pushDeliveryService = pushDeliveryService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<NotificationView> findAll(Long recipientUserId, NotificationType type, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<Notification> notifications = type == null
				? repository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(recipientUserId, pageable)
				: repository.findByRecipientUserIdAndTypeOrderByCreatedAtDescIdDesc(recipientUserId, type, pageable);
		return notifications.map(NotificationView::from);
	}

	@Transactional
	public void markAsRead(Long recipientUserId, Long notificationId) {
		Notification notification = repository.findByIdAndRecipientUserId(notificationId, recipientUserId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		notification.markAsRead(clock.instant());
	}

	@Transactional
	public void markAllAsRead(Long recipientUserId) {
		repository.markAllAsRead(recipientUserId, clock.instant());
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long recipientUserId) {
		return repository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
	}

	@Transactional
	public void createFromRealtime(RealtimeEventRecorded recorded) {
		RealtimeEventView event = recorded.event();
		NotificationTemplate template = templateFrom(event);
		if (template == null || repository.existsBySourceEventId(event.eventId())) return;

		Notification notification = repository.save(Notification.create(
				recorded.recipientUserId(), template.type(), template.title(), template.content(),
				event.reportId(), template.areaReportRequestId(), event.eventId()
		));
		pushDeliveryService.enqueue(notification);
	}

	private NotificationTemplate templateFrom(RealtimeEventView event) {
		if (event.eventType() != com.ojimasam.safy.realtime.domain.type.RealtimeEventType.AREA_REPORT_REQUEST) {
			return NotificationTemplate.from(event);
		}
		try {
			Long requestId = Long.valueOf(event.resourceId());
			AreaReportRequest request = areaReportRequestRepository.findById(requestId).orElse(null);
			return request == null ? null : new NotificationTemplate(
					NotificationType.SAFETY, request.getTitle(), request.getContent(), requestId);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private record NotificationTemplate(
			NotificationType type, String title, String content, Long areaReportRequestId) {
		private static NotificationTemplate from(RealtimeEventView event) {
			return switch (event.eventType()) {
				case REPORT_RECEIVED -> report("제보가 접수되었습니다", "제보 처리 상태를 확인해 주세요.");
				case REPORT_ASSIGNED -> report("새로운 제보가 배정되었습니다", "배정된 제보의 현장 정보를 확인해 주세요.");
				case REPORT_IN_PROGRESS -> report("제보를 확인하고 있습니다", "제보 처리 상태를 확인해 주세요.");
				case IMAGE_READY -> report("제보 이미지 처리가 완료되었습니다", "제보 이미지와 분석 준비 상태를 확인해 주세요.");
				case LIVE_READY -> report("라이브 연결이 준비되었습니다", "제보자의 라이브 현장 연결을 확인해 주세요.");
				case LIVE_ACTIVE -> report("라이브 송출이 시작되었습니다", "제보자의 실시간 현장 영상이 연결되었습니다.");
				case LIVE_DISCONNECTED -> report("라이브 연결이 끊어졌습니다", "제보자의 재연결을 기다리고 있습니다.");
				case LIVE_RECONNECTED -> report("라이브 연결이 복구되었습니다", "제보자의 실시간 현장 영상이 다시 연결되었습니다.");
				case LIVE_ENDED -> report("라이브 송출이 종료되었습니다", "제보의 현재 처리 상태를 확인해 주세요.");
				case REPORT_CLOSED -> report("제보 처리가 완료되었습니다", "등록한 제보의 처리 결과를 확인해 주세요.");
				case REPORT_REJECTED -> report("제보가 반려되었습니다", "반려 사유와 제보 처리 내용을 확인해 주세요.");
				case REPORT_CANCELLED -> report("제보가 취소되었습니다", "담당하던 제보가 제보자에 의해 취소되었습니다.");
				case REPORT_REASSIGNED -> null;
				case REPORT_ASSIGNMENT_STALLED -> report("제보 배정이 지연되고 있습니다", "대기 중인 제보를 확인해 주세요.");
				case RISK_ASSESSMENT_UPDATED -> report("제보 위험도 분석이 갱신되었습니다", "최신 위험도 분석 결과를 확인해 주세요.");
				case REPORT_MESSAGE, AREA_REPORT_REQUEST -> null;
			};
		}

		private static NotificationTemplate report(String title, String content) {
			return new NotificationTemplate(NotificationType.REPORT, title, content, null);
		}
	}
}
