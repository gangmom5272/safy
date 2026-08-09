package com.ojimasam.safy.live.application;
import com.ojimasam.safy.live.application.dto.LiveEgressResult;

/**
 * 미디어 서버가 보낸 웹훅을 서명 검증까지 마친 뒤 도메인이 이해하는 형태로 정규화한 이벤트.
 *
 * @param egress 녹화 관련 이벤트일 때만 채워진다.
 */
public record LiveWebhookEvent(Type type, String roomName, String participantIdentity, LiveEgressResult egress) {
	public LiveWebhookEvent(Type type, String roomName, String participantIdentity) {
		this(type, roomName, participantIdentity, null);
	}

	public enum Type {
		PARTICIPANT_JOINED,
		PARTICIPANT_LEFT,
		ROOM_FINISHED,
		EGRESS_STARTED,
		EGRESS_UPDATED,
		EGRESS_ENDED,
		/** 아직 처리하지 않는 이벤트. 무시한다. */
		UNSUPPORTED
	}
}
