package com.ojimasam.safy.realtime.application;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ojimasam.safy.realtime.domain.RealtimeEvent;
import com.ojimasam.safy.realtime.domain.RealtimeEventRepository;
import com.ojimasam.safy.realtime.domain.type.RealtimeEventType;
import com.ojimasam.safy.realtime.application.dto.RealtimeEventView;

@Service
public class RealtimeEventService {
	private static final int REPLAY_BATCH_SIZE = 100;

	private final RealtimeEventRepository repository;
	private final ApplicationEventPublisher eventPublisher;
	private final SseEventBroadcaster broadcaster;

	public RealtimeEventService(RealtimeEventRepository repository, ApplicationEventPublisher eventPublisher,
			SseEventBroadcaster broadcaster) {
		this.repository = repository;
		this.eventPublisher = eventPublisher;
		this.broadcaster = broadcaster;
	}

	@Transactional
	public void record(Long recipientUserId, RealtimeEventType eventType, Long reportId,
			String resourceId, Long messageId) {
		RealtimeEvent event = repository.save(
				RealtimeEvent.create(recipientUserId, eventType, reportId, resourceId, messageId));
		eventPublisher.publishEvent(new RealtimeEventRecorded(recipientUserId, RealtimeEventView.from(event)));
	}

	@Transactional(readOnly = true)
	public SseEmitter subscribe(Long userId, Long lastEventId) {
		SseEmitter emitter = broadcaster.subscribe(userId);
		if (lastEventId != null && lastEventId >= 0) {
			long replayCursor = lastEventId;
			List<RealtimeEvent> events;
			do {
				events = repository.findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(
						userId, replayCursor, PageRequest.of(0, REPLAY_BATCH_SIZE));
				for (RealtimeEvent event : events) {
					broadcaster.send(emitter, RealtimeEventView.from(event));
				}
				if (!events.isEmpty()) replayCursor = events.getLast().getId();
			} while (events.size() == REPLAY_BATCH_SIZE);
		}
		return emitter;
	}
}
