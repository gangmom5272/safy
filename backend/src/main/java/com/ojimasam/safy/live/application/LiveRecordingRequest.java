package com.ojimasam.safy.live.application;

/**
 * 미디어 서버에 넘길 녹화 요청. 저장 위치(객체 키)는 애플리케이션이 정하고,
 * 스토리지 자격증명 등 전송 방법은 인프라 어댑터가 채운다.
 *
 * @param recordingObjectKey 녹화 원본(MP4)이 저장될 객체 키
 * @param framePrefix        주기적으로 추출된 프레임(JPEG)이 저장될 키 프리픽스
 */
public record LiveRecordingRequest(
		String roomName,
		String participantIdentity,
		String recordingObjectKey,
		String framePrefix
) {
}
