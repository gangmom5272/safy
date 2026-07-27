package com.ojimasam.safy.realtime.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ojimasam.safy.realtime.domain.RealtimeEvent;
import com.ojimasam.safy.realtime.domain.RealtimeEventRepository;
import com.ojimasam.safy.realtime.domain.RealtimeEventType;

@ExtendWith(MockitoExtension.class)
class RealtimeEventServiceTest {
	@Mock RealtimeEventRepository repository;
	@Mock ApplicationEventPublisher publisher;
	@Mock SseEventBroadcaster broadcaster;

	@Test
	void persistsEventBeforePublishingIt() {
		when(repository.save(any())).thenAnswer(invocation -> {
			RealtimeEvent event = invocation.getArgument(0);
			ReflectionTestUtils.setField(event, "id", 100L);
			return event;
		});
		RealtimeEventService service = new RealtimeEventService(repository, publisher, broadcaster);

		service.record(30L, RealtimeEventType.REPORT_ASSIGNED, 10L, null, null);

		verify(repository).save(any(RealtimeEvent.class));
		verify(publisher).publishEvent(any(RealtimeEventRecorded.class));
	}

	@Test
	void replaysEventsAfterLastEventId() {
		SseEmitter emitter = new SseEmitter();
		RealtimeEvent event = RealtimeEvent.create(30L, RealtimeEventType.LIVE_ACTIVE, 10L, "room-id", null);
		ReflectionTestUtils.setField(event, "id", 101L);
		when(broadcaster.subscribe(30L)).thenReturn(emitter);
		when(repository.findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(
				org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(100L), any()))
				.thenReturn(List.of(event));
		RealtimeEventService service = new RealtimeEventService(repository, publisher, broadcaster);

		service.subscribe(30L, 100L);

		verify(broadcaster).send(org.mockito.ArgumentMatchers.eq(emitter), any(RealtimeEventView.class));
	}

	@Test
	void replaysMoreThanOneBatchWithoutMissingEvents() {
		SseEmitter emitter = new SseEmitter();
		List<RealtimeEvent> firstBatch = events(101L, 200L);
		List<RealtimeEvent> secondBatch = events(201L, 205L);
		when(broadcaster.subscribe(30L)).thenReturn(emitter);
		when(repository.findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(eq(30L), eq(100L), any()))
				.thenReturn(firstBatch);
		when(repository.findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(eq(30L), eq(200L), any()))
				.thenReturn(secondBatch);
		RealtimeEventService service = new RealtimeEventService(repository, publisher, broadcaster);

		service.subscribe(30L, 100L);

		verify(broadcaster, times(105)).send(eq(emitter), any(RealtimeEventView.class));
	}

	private List<RealtimeEvent> events(long firstId, long lastId) {
		List<RealtimeEvent> events = new ArrayList<>();
		for (long id = firstId; id <= lastId; id++) {
			RealtimeEvent event = RealtimeEvent.create(
					30L, RealtimeEventType.REPORT_ASSIGNED, id, null, null);
			ReflectionTestUtils.setField(event, "id", id);
			events.add(event);
		}
		return events;
	}
}
