package com.ojimasam.safy.administrativearea.application;

import java.util.List;

/**
 * 자치구·서울시 121장소로는 찾을 수 없는 구체적인 장소를 검색한다("멀티캠퍼스 역삼지점" 등).
 *
 * <p>외부 검색이라 결과에 우리 식별자가 없다. 좌표만 확실하므로, 상황 요약은 그 좌표를
 * 기준으로 계산한다({@code CoordinateSituationQueryService}).
 */
public interface PlaceKeywordSearcher {
	/** 검색 실패는 예외 대신 빈 목록이다 — 외부 장애가 자치구·121장소 검색까지 막으면 안 된다. */
	List<KeywordPlace> search(String keyword, int limit);
}
