package com.ojimasam.safy.grid.domain;

import java.util.List;

public interface PlaceGridMappingRepository {
	/**
	 * 매핑 전부. 121곳이 덮는 7,346행이고 장소 경계가 바뀌지 않는 한 늘지 않는다.
	 *
	 * <p>셀별 최고 혼잡도는 자바에서 계산한다. {@code CongestionLevel} 이 문자열로
	 * 저장돼 DB 에서 MAX 를 쓰면 알파벳 순(CROWDED &lt; NORMAL)이 되어 틀린다.
	 */
	List<PlaceGridMapping> findAll();

	PlaceGridMapping save(PlaceGridMapping mapping);

	long count();
}