package com.ojimasam.safy.admin.application.event;

/**
 * 담당자가 오프라인으로 전환됨(수동 전환 또는 연결 끊김 유예 초과).
 * report 쪽 리스너가 미확인(ASSIGNED) 배정을 회수해 재배정한다.
 */
public record OperatorWentOfflineEvent(Long operatorId) {
}
