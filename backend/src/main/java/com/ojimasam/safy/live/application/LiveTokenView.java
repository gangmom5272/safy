package com.ojimasam.safy.live.application;

import java.time.Instant;
import java.util.UUID;

public record LiveTokenView(UUID streamRoomId, String serverUrl, String roomName,
		String participantIdentity, LiveParticipantRole participantType, String token, Instant expiresAt) {
}
