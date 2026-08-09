package com.ojimasam.safy.report.domain.type;

public enum ReportStatus {
	RECEIVED,
	ASSIGNED,
	IN_PROGRESS,
	/** 담당자가 진짜 제보로 승인해 시민 지도에 반영됨. 대응은 계속 진행 중이다. */
	CONFIRMED,
	CLOSED,
	REJECTED,
	CANCELLED
}
