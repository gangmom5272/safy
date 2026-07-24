package com.ojimasam.safy.live.application;

import com.ojimasam.safy.live.domain.LiveSessionStatus;

public enum StreamStatus {
	CONNECTING,
	LIVE,
	ENDED;

	public static StreamStatus from(LiveSessionStatus status) {
		return switch (status) {
			case CREATED -> CONNECTING;
			case ACTIVE -> LIVE;
			case ENDED, FAILED -> ENDED;
		};
	}
}
