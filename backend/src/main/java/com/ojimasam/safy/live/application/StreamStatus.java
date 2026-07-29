package com.ojimasam.safy.live.application;

import com.ojimasam.safy.live.domain.type.LiveSessionStatus;

public enum StreamStatus {
	CONNECTING,
	LIVE,
	/** 연결 끊김 · 재접속 대기중 */
	DISCONNECTED,
	ENDED;

	public static StreamStatus from(LiveSessionStatus status) {
		return switch (status) {
			case CREATED -> CONNECTING;
			case ACTIVE -> LIVE;
			case DISCONNECTED -> DISCONNECTED;
			case ENDED, FAILED -> ENDED;
		};
	}
}
