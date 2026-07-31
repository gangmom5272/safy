package com.ojimasam.safy.report.domain.type;

/**
 * 시민 피드 노출 여부. 처리 진행 상태({@link ReportStatus})와 독립된 축이다.
 * 담당자가 정상 제보로 판정(업무 종료)해야 PUBLISHED 가 되고, 부적절 판명 시 WITHHELD 로 내린다.
 */
public enum ReportPublicVisibility {
	PENDING,
	PUBLISHED,
	WITHHELD
}
