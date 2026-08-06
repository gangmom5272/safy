package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

import com.ojimasam.safy.report.domain.type.ReportStatus;

/**
 * 단계별 진행 바에 찍을 시각 요약.
 *
 * <p>재배정·이관이 있으면 제보는 처리중과 접수 사이를 왕복하므로 "처리중이 된 시각"이
 * 여러 개다. 여기서는 <b>최초</b> 진입 시각을 준다 — 마지막 값을 쓰면 재배정할 때마다
 * 과거가 지워져 대응이 늦었는지 판단할 수 없다. 왕복한 사실은
 * {@code reassignCount}·{@code transferCount} 로 드러난다.
 */
public record ReportHandlingStagesView(
		/** 시민이 제보한 시각. */
		Instant receivedAt,
		/** 담당자가 처음 상세를 열어 확인한 시각. 아직 아무도 안 열었으면 null. */
		Instant firstInProgressAt,
		/** 종결 시각(종료·반려·취소). 진행 중이면 null. */
		Instant closedAt,
		ReportStatus currentStatus,
		/** 담당자가 같은 센터 안에서 넘긴 횟수. */
		int reassignCount,
		/** 관제센터가 바뀐 횟수(수동 이관 + 자동 이관). */
		int transferCount
) {
}
