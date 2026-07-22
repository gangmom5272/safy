package com.ojimasam.safy.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.ojimasam.safy.common.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "아이디 또는 비밀번호가 올바르지 않습니다."),
	INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "소셜 인증 토큰이 유효하지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "Refresh Token이 유효하지 않습니다."),
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_004", "지원하지 않는 소셜 로그인 제공자입니다."),
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_005", "정지된 계정입니다."),
	ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "AUTH_006", "탈퇴한 계정입니다."),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_007", "해당 요청에 대한 권한이 없습니다."),
	AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_008", "인증이 필요합니다."),
	PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_009", "현재 비밀번호가 일치하지 않습니다."),
	LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "AUTH_010", "이미 사용 중인 관리자 아이디입니다."),
	LOGIN_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_011", "로그인 시도가 많아 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	AuthErrorCode(HttpStatus status, String code, String message) {
		this.status = status; this.code = code; this.message = message;
	}

	public HttpStatus getStatus() { return status; }
	public String getCode() { return code; }
	public String getMessage() { return message; }
}
