package com.ojimasam.safy.live.application.dto;

import java.time.Instant;
import java.util.UUID;
import com.ojimasam.safy.live.application.LiveParticipantRole;

public record LiveTokenView(UUID streamRoomId, String serverUrl, String roomName,
		String participantIdentity, LiveParticipantRole participantType, String token, Instant expiresAt) {
}
