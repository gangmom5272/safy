package com.ojimasam.safy.realtime.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ojimasam.safy.realtime.application.RealtimeEventRecorded;
import com.ojimasam.safy.realtime.application.SseEventBroadcaster;

@Component
public class RealtimeEventDeliveryListener {
	private final SseEventBroadcaster broadcaster;

	public RealtimeEventDeliveryListener(SseEventBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void deliver(RealtimeEventRecorded recorded) {
		broadcaster.broadcast(recorded.recipientUserId(), recorded.event());
	}
}
