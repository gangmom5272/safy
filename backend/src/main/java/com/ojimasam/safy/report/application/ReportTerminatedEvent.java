package com.ojimasam.safy.report.application;

import com.ojimasam.safy.report.domain.ReportStatus;

/**
 * 제보가 담당자에 의해 종결(CLOSED/REJECTED)됐을 때 발행된다.
 * 라이브 모듈이 이를 구독해 진행 중인 스트림 세션을 종료하고 제보자에게 통지한다.
 */
public record ReportTerminatedEvent(Long reportId, Long reporterId, ReportStatus finalStatus) {
}
