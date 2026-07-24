package com.ojimasam.safy.live.domain;

public class LiveSessionAccessDeniedException extends RuntimeException {
	public LiveSessionAccessDeniedException() {
		super("해당 라이브 세션에 접근할 수 없습니다.");
	}
}
