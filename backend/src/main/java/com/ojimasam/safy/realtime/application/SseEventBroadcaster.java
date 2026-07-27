package com.ojimasam.safy.realtime.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEventBroadcaster {
	private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;
	private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long userId) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
		emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
		emitter.onCompletion(() -> remove(userId, emitter));
		emitter.onTimeout(() -> remove(userId, emitter));
		emitter.onError(exception -> remove(userId, emitter));
		try {
			emitter.send(SseEmitter.event().name("CONNECTED").reconnectTime(3000).data(Map.of("connected", true)));
		} catch (IOException exception) {
			remove(userId, emitter);
		}
		return emitter;
	}

	public void broadcast(Long userId, RealtimeEventView event) {
		for (SseEmitter emitter : emitters.getOrDefault(userId, new CopyOnWriteArrayList<>())) {
			send(emitter, event);
		}
	}

	public void send(SseEmitter emitter, RealtimeEventView event) {
		try {
			emitter.send(SseEmitter.event()
					.id(event.eventId().toString())
					.name(event.eventType().name())
					.data(event));
		} catch (IOException | IllegalStateException exception) {
			removeEverywhere(emitter);
		}
	}

	@Scheduled(fixedDelay = 15000)
	public void heartbeat() {
		for (List<SseEmitter> userEmitters : emitters.values()) {
			for (SseEmitter emitter : userEmitters) {
				try {
					emitter.send(SseEmitter.event().comment("heartbeat"));
				} catch (IOException | IllegalStateException exception) {
					removeEverywhere(emitter);
				}
			}
		}
	}

	private void remove(Long userId, SseEmitter emitter) {
		List<SseEmitter> userEmitters = emitters.get(userId);
		if (userEmitters == null) return;
		userEmitters.remove(emitter);
		if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
	}

	private void removeEverywhere(SseEmitter emitter) {
		emitters.forEach((userId, ignored) -> remove(userId, emitter));
	}
}
