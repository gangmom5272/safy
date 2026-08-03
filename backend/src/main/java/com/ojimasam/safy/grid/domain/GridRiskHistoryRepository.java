package com.ojimasam.safy.grid.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface GridRiskHistoryRepository {
	GridRiskHistory save(GridRiskHistory history);

	/**
	 * 셀별 가장 최근 이력. 값이 바뀌었는지 판단하는 기준이고 지도 조회의 기본 질의다.
	 *
	 * <p>이력은 <b>값이 바뀔 때만</b> 쌓이므로 "최신"이 몇 시간 전일 수 있다. 그래서
	 * 특정 창으로 조회하면 안 되고 셀별 최댓값을 찾아야 한다.
	 */
	List<GridRiskHistory> findLatestPerGridCell();

	/** 주어진 셀들의 가장 최근 이력. 지도 뷰포트 조회에 쓴다. */
	List<GridRiskHistory> findLatestByGridCellIdIn(Collection<Long> gridCellIds);

	/** 한 셀의 추이. 최근 것부터. */
	List<GridRiskHistory> findAllByGridCellIdAndWindowEndedAtAfterOrderByWindowEndedAtDesc(
			Long gridCellId, Instant after);
}
