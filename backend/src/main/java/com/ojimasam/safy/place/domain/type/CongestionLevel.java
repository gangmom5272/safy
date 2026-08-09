package com.ojimasam.safy.place.domain.type;

import java.util.Optional;

/**
 * 서울시 실시간 도시데이터의 인구 혼잡도 4단계.
 * FastAPI가 원본 한글 값을 그대로 보낼지 영문 이름으로 변환해 보낼지 확정되지 않았으므로
 * 두 표기를 모두 받아들인다.
 */
public enum CongestionLevel {
	RELAXED("여유"),
	NORMAL("보통"),
	SLIGHTLY_CROWDED("약간 붐빔"),
	CROWDED("붐빔");

	private final String label;

	CongestionLevel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static Optional<CongestionLevel> from(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = collapseWhitespace(value);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}
		for (CongestionLevel level : values()) {
			if (collapseWhitespace(level.label).equalsIgnoreCase(normalized)
					|| level.name().equalsIgnoreCase(normalized)) {
				return Optional.of(level);
			}
		}
		return Optional.empty();
	}

	/**
	 * '약간 붐빔' 같은 값의 공백이 상류에서 제거되거나 늘어나도 같은 단계로 인식해야 한다.
	 * 이 매칭이 실패하면 그 지역의 관측값이 조용히 버려진다.
	 */
	private static String collapseWhitespace(String value) {
		return value.replaceAll("\\s+", "").trim();
	}
}
