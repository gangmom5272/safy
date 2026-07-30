package com.ojimasam.safy.place.domain.type;

import java.util.Optional;

/**
 * 서울시가 주요 121장소를 분류한 구분값.
 * 같은 '붐빔'이라도 지하철역과 공원의 의미가 달라, 지도 표현과 위험도 해석에서 구분이 필요하다.
 */
public enum PlaceCategory {
	TOURIST_ZONE("관광특구"),
	HERITAGE("고궁·문화유산"),
	POPULATION_DENSE("인구밀집지역"),
	COMMERCIAL("발달상권"),
	PARK("공원");

	private final String label;

	PlaceCategory(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static Optional<PlaceCategory> from(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = value.trim();
		for (PlaceCategory category : values()) {
			if (category.label.equals(normalized) || category.name().equalsIgnoreCase(normalized)) {
				return Optional.of(category);
			}
		}
		return Optional.empty();
	}
}
