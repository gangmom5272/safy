package com.ojimasam.safy.realtime.domain.type;

public enum RealtimeEventType {
	REPORT_ASSIGNED,
	IMAGE_READY,
	LIVE_READY,
	LIVE_ACTIVE,
	LIVE_DISCONNECTED,
	LIVE_RECONNECTED,
	LIVE_ENDED,
	REPORT_CLOSED,
	REPORT_REJECTED,
	REPORT_CANCELLED,
	/** 담당자가 재배정을 요청해 담당자가 바뀜. 제보와 라이브 송출은 그대로 유지된다. */
	REPORT_REASSIGNED,
	REPORT_MESSAGE,
	/** 배정할 근무중 담당자도, 이관할 백업 센터도 없어 제보가 멈춤 → SUPER_ADMIN에게 알림. */
	REPORT_ASSIGNMENT_STALLED
}
