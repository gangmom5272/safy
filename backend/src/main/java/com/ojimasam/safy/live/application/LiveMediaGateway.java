package com.ojimasam.safy.live.application;

import java.time.Duration;

public interface LiveMediaGateway {
	void createRoom(String roomName);
	String issueToken(String roomName, String identity, LiveParticipantRole role, Duration ttl);
	void closeRoom(String roomName);
	String serverUrl();

	/**
	 * 제보자 트랙 녹화를 시작한다. 미디어 서버가 스토리지에 직접 업로드하므로
	 * 영상 데이터는 애플리케이션을 거치지 않는다.
	 *
	 * @return 미디어 서버가 부여한 녹화 작업 ID
	 */
	String startRecording(LiveRecordingRequest request);

	/** 녹화를 중단한다. 이미 끝난 작업이면 조용히 무시한다. */
	void stopRecording(String providerEgressId);
}
