package com.ojimasam.safy.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.ojimasam.safy.common.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
	INVALID_USER_STATUS(HttpStatus.BAD_REQUEST, "USER_002", "변경할 수 없는 사용자 상태입니다."),
	CITIZEN_ONLY_OPERATION(HttpStatus.FORBIDDEN, "USER_003", "시민 사용자만 탈퇴할 수 있습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
	UserErrorCode(HttpStatus status, String code, String message) {
		this.status = status; this.code = code; this.message = message;
	}
	public HttpStatus getStatus() { return status; }
	public String getCode() { return code; }
	public String getMessage() { return message; }
}
