package com.ojimasam.safy.live.infrastructure;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ojimasam.safy.live.application.LiveEgressResult;
import com.ojimasam.safy.live.application.LiveMediaException;
import com.ojimasam.safy.live.application.LiveWebhookEvent;
import com.ojimasam.safy.live.application.LiveWebhookParser;
import com.ojimasam.safy.live.application.OpenViduProperties;

import io.livekit.server.WebhookReceiver;
import livekit.LivekitEgress;
import livekit.LivekitWebhook;

@Component
public class LiveKitWebhookParser implements LiveWebhookParser {
	private final OpenViduProperties properties;

	public LiveKitWebhookParser(OpenViduProperties properties) {
		this.properties = properties;
	}

	@Override
	public LiveWebhookEvent parse(String rawBody, String authorizationHeader) {
		if (rawBody == null || rawBody.isBlank()) {
			throw new LiveMediaException("Webhook body is empty");
		}
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			throw new LiveMediaException("Webhook signature header is missing");
		}
		ensureConfigured();
		LivekitWebhook.WebhookEvent event;
		try {
			event = new WebhookReceiver(properties.apiKey(), properties.apiSecret())
					.receive(rawBody, authorizationHeader);
		} catch (RuntimeException exception) {
			throw new LiveMediaException("Webhook signature verification failed", exception);
		}
		if (event == null) throw new LiveMediaException("Webhook payload could not be parsed");
		LiveWebhookEvent.Type type = toType(event.getEvent());
		return new LiveWebhookEvent(
				type,
				roomName(event),
				event.hasParticipant() ? event.getParticipant().getIdentity() : null,
				isEgress(type) ? toEgressResult(event.getEgressInfo()) : null
		);
	}

	/** 녹화 이벤트는 room 대신 egressInfo에 방 이름이 담겨 온다. */
	private String roomName(LivekitWebhook.WebhookEvent event) {
		if (event.hasRoom() && !event.getRoom().getName().isBlank()) return event.getRoom().getName();
		String egressRoom = event.getEgressInfo().getRoomName();
		return egressRoom == null || egressRoom.isBlank() ? null : egressRoom;
	}

	private LiveEgressResult toEgressResult(LivekitEgress.EgressInfo info) {
		if (info == null) return null;
		LivekitEgress.EgressStatus status = info.getStatus();
		boolean failed = status == LivekitEgress.EgressStatus.EGRESS_FAILED
				|| status == LivekitEgress.EgressStatus.EGRESS_ABORTED
				|| status == LivekitEgress.EgressStatus.EGRESS_LIMIT_REACHED;
		List<LiveEgressResult.LiveEgressFile> files = info.getFileResultsList().stream()
				.map(file -> new LiveEgressResult.LiveEgressFile(
						file.getFilename(), file.getSize(), toMillis(file.getDuration())))
				.toList();
		return new LiveEgressResult(info.getEgressId(), failed, info.getError(), files);
	}

	/** LiveKit은 재생 길이를 나노초로 준다. */
	private Long toMillis(long durationNanos) {
		return durationNanos <= 0 ? null : durationNanos / 1_000_000L;
	}

	private boolean isEgress(LiveWebhookEvent.Type type) {
		return type == LiveWebhookEvent.Type.EGRESS_STARTED
				|| type == LiveWebhookEvent.Type.EGRESS_UPDATED
				|| type == LiveWebhookEvent.Type.EGRESS_ENDED;
	}

	private LiveWebhookEvent.Type toType(String event) {
		if (event == null) return LiveWebhookEvent.Type.UNSUPPORTED;
		return switch (event) {
			case "participant_joined" -> LiveWebhookEvent.Type.PARTICIPANT_JOINED;
			case "participant_left" -> LiveWebhookEvent.Type.PARTICIPANT_LEFT;
			case "room_finished" -> LiveWebhookEvent.Type.ROOM_FINISHED;
			case "egress_started" -> LiveWebhookEvent.Type.EGRESS_STARTED;
			case "egress_updated" -> LiveWebhookEvent.Type.EGRESS_UPDATED;
			case "egress_ended" -> LiveWebhookEvent.Type.EGRESS_ENDED;
			default -> LiveWebhookEvent.Type.UNSUPPORTED;
		};
	}

	private void ensureConfigured() {
		if (isBlank(properties.apiKey()) || isBlank(properties.apiSecret())) {
			throw new LiveMediaException("OpenVidu configuration is missing");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
