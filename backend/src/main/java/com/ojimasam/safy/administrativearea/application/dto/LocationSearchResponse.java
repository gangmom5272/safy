package com.ojimasam.safy.administrativearea.application.dto;

import java.math.BigDecimal;

import com.ojimasam.safy.administrativearea.application.KeywordPlace;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.place.domain.Place;

/**
 * 지역명·장소명 통합 검색 결과 한 건.
 *
 * <p>{@code type} 으로 상황 요약을 어디에 물을지 정한다.
 *
 * <ul>
 *   <li>{@code AREA} — 자치구. {@code /areas/{id}/status}</li>
 *   <li>{@code PLACE} — 서울시 121장소. {@code /places/{id}/status}</li>
 *   <li>{@code KEYWORD} — 외부 검색으로 찾은 구체적 장소. 우리 식별자가 없어 좌표로 묻는다
 *       ({@code /locations/status?latitude=&longitude=})</li>
 * </ul>
 *
 * <p>자치구만 좌표가 없다(지금까지도 없었다 — 프론트가 자치구 중심 좌표로 보강해 왔다).
 * 나머지 둘은 좌표가 곧 기준점이라 항상 채워진다.
 *
 * @param id {@code KEYWORD} 는 우리 DB에 없는 장소라 {@code null} 이다.
 */
public record LocationSearchResponse(
		String type,
		Long id,
		String name,
		String displayName,
		BigDecimal latitude,
		BigDecimal longitude
) {
	public static final String TYPE_AREA = "AREA";
	public static final String TYPE_PLACE = "PLACE";
	public static final String TYPE_KEYWORD = "KEYWORD";

	public static LocationSearchResponse fromArea(AdministrativeArea area) {
		return new LocationSearchResponse(TYPE_AREA, area.getId(), area.getName(), area.getName(), null, null);
	}

	public static LocationSearchResponse fromPlace(Place place) {
		return new LocationSearchResponse(
				TYPE_PLACE, place.getId(), place.getName(), place.getName(),
				place.getCenterLatitude(), place.getCenterLongitude());
	}

	/** {@code displayName} 에 주소를 담는다 — 같은 이름의 지점이 여러 개라 구분이 필요하다. */
	public static LocationSearchResponse fromKeywordPlace(KeywordPlace place) {
		return new LocationSearchResponse(
				TYPE_KEYWORD, null, place.name(), place.address(), place.latitude(), place.longitude());
	}
}
