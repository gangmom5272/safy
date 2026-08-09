package com.ojimasam.safy.grid.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GridCellRepository {
	Optional<GridCell> findByGridCode(String gridCode);

	List<GridCell> findAllByGridCodeIn(Collection<String> gridCodes);

	List<GridCell> findAll();

	/**
	 * 지도 뷰포트 안의 셀. 셀 <b>중심</b>이 범위에 들어오는 것만 잡히므로 경계에 걸친
	 * 셀은 빠지는데, 100m 격자에서 한 칸 차이는 화면 밖이라 문제가 되지 않는다.
	 */
	List<GridCell> findAllByCenterLatitudeBetweenAndCenterLongitudeBetween(
			BigDecimal minLatitude, BigDecimal maxLatitude,
			BigDecimal minLongitude, BigDecimal maxLongitude);

	GridCell save(GridCell gridCell);
}
