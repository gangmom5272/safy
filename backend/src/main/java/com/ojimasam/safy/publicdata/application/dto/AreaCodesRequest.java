package com.ojimasam.safy.publicdata.application.dto;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 수집 대상 장소 코드 목록(서울시 AREA_CD).
 *
 * 서울시 citydata API 는 AREA_NM 파라미터로 장소 하나씩만 조회되므로,
 * FastAPI 가 이 목록을 나눠 병렬 호출한 뒤 하나의 응답으로 합쳐서 돌려준다.
 * 목록의 단일 진실 원천은 places 테이블이고, FastAPI 는 목록을 따로 보관하지 않는다.
 *
 * URL 길이 제약과 목록 표현을 피하려고 GET 대신 POST 로 보낸다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AreaCodesRequest(
		List<String> areaCodes
) {
}
