package com.ojimasam.safy.controlcenter.domain.type;

import org.springframework.http.HttpStatus;

import com.ojimasam.safy.common.exception.ErrorCode;

public enum ControlCenterErrorCode implements ErrorCode {
	CONTROL_CENTER_NOT_FOUND(HttpStatus.NOT_FOUND, "CENTER_001", "관제센터를 찾을 수 없습니다."),
	CONTROL_CENTER_CODE_DUPLICATED(HttpStatus.CONFLICT, "CENTER_002", "이미 사용 중인 관제센터 코드입니다."),
	CONTROL_CENTER_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "CENTER_003", "관제센터 담당 구역을 찾을 수 없습니다."),
	CONTROL_CENTER_AREA_DUPLICATED(HttpStatus.CONFLICT, "CENTER_004", "이미 설정된 관제센터 담당 구역입니다."),
	CONTROL_CENTER_HAS_ACTIVE_OPERATOR(HttpStatus.CONFLICT, "CENTER_005", "활성 관리자가 소속된 관제센터는 비활성화할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	ControlCenterErrorCode(HttpStatus status, String code, String message) {
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
