package com.ojimasam.safy.live.application;

import java.time.Duration;

public interface LiveMediaGateway {
	void createRoom(String roomName);
	String issueToken(String roomName, String identity, LiveParticipantRole role, Duration ttl);
	void closeRoom(String roomName);
	String serverUrl();
}
