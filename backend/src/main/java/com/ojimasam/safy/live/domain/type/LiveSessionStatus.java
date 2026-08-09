package com.ojimasam.safy.live.domain.type;

public enum LiveSessionStatus {
	CREATED,
	ACTIVE,
	/** 제보자 연결이 끊겨 재접속을 기다리는 상태. 유예 시간이 지나면 ENDED로 정리된다. */
	DISCONNECTED,
	ENDED,
	FAILED
}
