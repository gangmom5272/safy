package com.ojimasam.safy.admin.domain;

import org.springframework.http.HttpStatus;

import com.ojimasam.safy.common.exception.ErrorCode;

public enum AdminErrorCode implements ErrorCode {
	CONTROL_CENTER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_001", "관제센터를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
	AdminErrorCode(HttpStatus status, String code, String message) {
		this.status = status; this.code = code; this.message = message;
	}
	public HttpStatus getStatus() { return status; }
	public String getCode() { return code; }
	public String getMessage() { return message; }
}
