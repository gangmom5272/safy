package com.ojimasam.safy.live.domain;

public enum StreamRecordingStatus {
	/** 미디어 서버에 녹화를 요청했고 아직 시작 통지를 받지 못한 상태. */
	STARTING,
	ACTIVE,
	COMPLETED,
	FAILED,
	/** 녹화된 파일 없이 중단됨. */
	ABORTED
}
