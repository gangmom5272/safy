package com.ojimasam.safy.report.application.dto;

/**
 * 담당 이력 타임라인의 사건 종류.
 *
 * <p>제보 상태(RECEIVED/ASSIGNED/IN_PROGRESS…)와 다른 축이다. 상태는 재배정마다
 * ASSIGNED → RECEIVED → ASSIGNED 로 왕복하므로 "무슨 일이 있었는가"를 표현하지 못한다.
 * 특히 재배정과 이관은 상태 전이가 완전히 같아서 상태만으로는 영원히 구분되지 않는다.
 */
public enum ReportHandlingEventType {
	/** 센터 안에서 자동 배정됨. */
	AUTO_ASSIGNED,
	/** 담당자가 공용 큐에서 직접 담당함. */
	CLAIMED,
	/** 담당자가 상세를 열어 처리중으로 전이됨. */
	VIEWED,
	/** 담당자가 진짜 제보로 승인해 시민 지도에 반영됨. 종결이 아니라 대응은 계속된다. */
	CONFIRMED,
	/** 업무 종료. */
	COMPLETED,
	/** 반려로 종결. */
	REJECTED,
	/** 담당자가 같은 센터의 다른 담당자에게 넘김. */
	REASSIGNED,
	/** 담당자가 다른 관제센터로 이관함. */
	TRANSFERRED,
	/** 근무중 담당자 0명이라 시스템이 다른 센터로 이관함. 담당자가 없는 사건이다. */
	AUTO_TRANSFERRED,
	/** 배정 후 유예 시간 안에 확인하지 않아 회수됨. */
	ASSIGNMENT_TIMEOUT,
	/** 담당자가 오프라인으로 전환되어 회수됨. */
	OPERATOR_OFFLINE,
	/** 담당자가 자리비움으로 전환되어 회수됨. */
	OPERATOR_AWAY,
	/** 제보자가 제보를 취소해 배정이 끝남. */
	REPORTER_CANCELLED
}
