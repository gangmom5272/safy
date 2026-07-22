package com.ojimasam.safy.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 본문을 읽을 수 없습니다."),
	TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_003", "요청 값의 타입이 올바르지 않습니다."),
	MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_004", "필수 요청 값이 누락되었습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_005", "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "지원하지 않는 HTTP 메서드입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_007", "지원하지 않는 미디어 타입입니다."),
	FILE_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "COMMON_008", "업로드 가능한 파일 크기를 초과했습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_009", "인증이 필요합니다."),
	CONFLICT(HttpStatus.CONFLICT, "COMMON_010", "현재 상태에서는 요청을 처리할 수 없습니다."),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "COMMON_011", "다른 요청에서 정보가 변경되었습니다. 다시 시도해 주세요."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_012", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	CommonErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
