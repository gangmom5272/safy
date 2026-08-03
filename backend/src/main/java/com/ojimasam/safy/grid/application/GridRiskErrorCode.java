package com.ojimasam.safy.grid.application;

import org.springframework.http.HttpStatus;

import com.ojimasam.safy.common.exception.ErrorCode;

public enum GridRiskErrorCode implements ErrorCode {
	INVALID_BOUNDS(HttpStatus.BAD_REQUEST, "GRID_001", "조회 범위가 올바르지 않습니다."),
	BOUNDS_TOO_WIDE(HttpStatus.BAD_REQUEST, "GRID_002", "조회 범위가 너무 넓습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	GridRiskErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override public HttpStatus getStatus() { return status; }
	@Override public String getCode() { return code; }
	@Override public String getMessage() { return message; }
}
