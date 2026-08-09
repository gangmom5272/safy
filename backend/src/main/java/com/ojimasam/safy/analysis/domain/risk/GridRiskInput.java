package com.ojimasam.safy.analysis.domain.risk;

import java.util.List;

import com.ojimasam.safy.place.domain.type.CongestionLevel;

/**
 * 격자 1개의 위험도를 계산하기 위한 입력.
 *
 * @param gridCellId 대상 격자 셀
 * @param reportObservations 시간 창 안에 이 격자로 들어온 제보들
 * @param publicCongestion 이 격자에 매핑된 121곳 장소의 최신 혼잡도. 없으면 null
 */
public record GridRiskInput(
		Long gridCellId,
		List<GridObservation> reportObservations,
		CongestionLevel publicCongestion
) {
}
