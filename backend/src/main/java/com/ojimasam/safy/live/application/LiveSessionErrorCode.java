package com.ojimasam.safy.live.application;

import org.springframework.http.HttpStatus;
import com.ojimasam.safy.common.exception.ErrorCode;

public enum LiveSessionErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "LIVE_001", "라이브 세션을 찾을 수 없습니다."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "LIVE_002", "해당 라이브 세션에 접근할 수 없습니다."),
	NOT_JOINABLE(HttpStatus.CONFLICT, "LIVE_003", "참여할 수 없는 라이브 세션입니다."),
	MEDIA_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "LIVE_004", "미디어 서버에 연결할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	LiveSessionErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override public HttpStatus getStatus() { return status; }
	@Override public String getCode() { return code; }
	@Override public String getMessage() { return message; }
}
