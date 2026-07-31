package com.ojimasam.safy.report.domain.type;

/**
 * 배정이 CANCELLED 로 끝난 사유. 자동 배정의 되돌아옴 방지(제외 목록)는 담당자가
 * 스스로 놓아준 사유만 포함한다 — 무응답 회수(타임아웃·오프라인)까지 영구 제외하면
 * 후보가 말라 자동 배정이 조용히 죽는다.
 */
public enum ReportAssignmentCancelReason {
	/** 담당자가 재배정을 요청해 스스로 놓음. */
	REASSIGN_REQUESTED(true),
	/** 담당자가 다른 관제센터로 이관하며 놓음. */
	TRANSFERRED(true),
	/** 배정 후 유예 시간 안에 확인하지 않아 회수됨. */
	ASSIGNMENT_TIMEOUT(false),
	/** 담당자가 오프라인으로 전환되어 회수됨. */
	OPERATOR_OFFLINE(false),
	/** 제보자가 제보를 취소함. */
	REPORTER_CANCELLED(false),
	/** 제보가 반려(REJECTED)로 종결됨. */
	REPORT_REJECTED(false);

	private final boolean voluntaryRelease;

	ReportAssignmentCancelReason(boolean voluntaryRelease) {
		this.voluntaryRelease = voluntaryRelease;
	}

	public boolean isVoluntaryRelease() {
		return voluntaryRelease;
	}
}
