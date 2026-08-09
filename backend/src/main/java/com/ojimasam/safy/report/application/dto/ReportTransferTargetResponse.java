package com.ojimasam.safy.report.application.dto;

import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;

/**
 * 이관 대상 후보 센터 한 곳. 담당자는 센터 목록 API(SUPER_ADMIN 전용)를 볼 수 없으므로
 * 이관 화면의 선택지는 이 응답으로만 채운다.
 */
public record ReportTransferTargetResponse(
		Long controlCenterId,
		String controlCenterName,
		/** 이 행정구역에 대한 담당 역할(주 담당/예비 담당). 응답 순서도 주 담당이 먼저다. */
		CoverageRole coverageRole,
		int priority,
		boolean hasOnDutyOperator,
		/** 이 제보가 이미 거쳐온 센터인지. */
		boolean visited,
		/**
		 * 지금 이관할 수 있는지. 거쳐온 센터는 근무중 담당자가 있을 때만 허용한다는 규칙을
		 * 서버가 계산해 내려준다(클라이언트가 규칙을 복제하면 어긋난다).
		 */
		boolean transferable
) {
}
