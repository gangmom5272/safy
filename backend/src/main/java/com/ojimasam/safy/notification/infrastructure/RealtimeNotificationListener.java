package com.ojimasam.safy.notification.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.notification.application.NotificationService;
import com.ojimasam.safy.realtime.application.RealtimeEventRecorded;

@Component
public class RealtimeNotificationListener {
	private final NotificationService notificationService;

	public RealtimeNotificationListener(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void createNotification(RealtimeEventRecorded recorded) {
		notificationService.createFromRealtime(recorded);
	}
}
