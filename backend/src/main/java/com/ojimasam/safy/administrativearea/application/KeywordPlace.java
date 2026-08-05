package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;

/**
 * 외부 키워드 검색으로 찾은 장소. 우리 DB에 없는 곳이라 식별자가 없고 좌표가 유일한 기준이다.
 *
 * @param address 사용자가 어느 곳인지 알아볼 수 있게 도로명(없으면 지번) 주소를 담는다.
 */
public record KeywordPlace(
		String name,
		String address,
		BigDecimal latitude,
		BigDecimal longitude
) {
}
