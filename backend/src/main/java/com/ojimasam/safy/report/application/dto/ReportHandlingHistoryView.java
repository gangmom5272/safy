package com.ojimasam.safy.report.application.dto;

import java.util.List;

/** 담당자 화면용 제보 처리 이력. 시민용 상태 이력과 달리 접거나 문구로 바꾸지 않는다. */
public record ReportHandlingHistoryView(
		Long reportId,
		ReportHandlingStagesView stages,
		/** 시간 오름차순. 같은 시각이면 놓은 사건이 받은 사건보다 앞에 온다. */
		List<ReportHandlingEventView> events
) {
}
