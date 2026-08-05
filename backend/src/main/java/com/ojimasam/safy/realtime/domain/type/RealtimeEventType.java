package com.ojimasam.safy.realtime.domain.type;

public enum RealtimeEventType {
	REPORT_RECEIVED,
	REPORT_ASSIGNED,
	REPORT_IN_PROGRESS,
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
	/** AI 프레임 집계 결과가 생성되거나 점수·등급·확인 필요 여부가 변경됨. */
	RISK_ASSESSMENT_UPDATED,
	/** 배정할 근무중 담당자도, 이관할 백업 센터도 없어 제보가 멈춤 → SUPER_ADMIN에게 알림. */
	REPORT_ASSIGNMENT_STALLED,
	/** 관제 담당자가 최근 해당 장소에 있던 시민에게 현장 제보를 요청함. */
	AREA_REPORT_REQUEST
}
