package com.ojimasam.safy.report.application.dto;

import java.time.Instant;

/** 담당 이력 타임라인의 한 줄. */
public record ReportHandlingEventView(
		ReportHandlingEventType type,
		Instant occurredAt,
		/** 조치한 담당자. 시스템 자동 이관은 null 이다. */
		Long operatorId,
		String operatorName,
		/** 이 조치가 일어난 관제센터. 이관에서는 보내는 쪽이다. */
		Long controlCenterId,
		String controlCenterName,
		/** 이관 목적지. TRANSFERRED·AUTO_TRANSFERRED 에서만 채워진다. */
		Long toControlCenterId,
		String toControlCenterName,
		/**
		 * 담당자가 입력한 원본 사유. 시민용 안내 문구로 바꾸지 않는다.
		 * 자동 회수(타임아웃·오프라인·자리비움)에는 사람이 적은 문장이 없어 null 이며,
		 * 그 경우 사유는 {@code type} 이 이미 담고 있다.
		 */
		String reason
) {
}
