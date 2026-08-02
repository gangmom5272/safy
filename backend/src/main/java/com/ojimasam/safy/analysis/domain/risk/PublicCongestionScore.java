package com.ojimasam.safy.analysis.domain.risk;

import com.ojimasam.safy.place.domain.type.CongestionLevel;

/**
 * 서울시 실시간 도시데이터의 인구 혼잡도 4단계를 SAFY 위험 점수로 바꾼다.
 *
 * <p>서울시 척도는 보행 편의성 기준이며 위험도 척도가 아니다. 최고 등급 '붐빔'도
 * "도보 이동이 어렵다" 수준이므로, 공공데이터만으로는 MEDIUM 을 넘지 않도록
 * 붐빔을 60({@link RiskLevel} HIGH 시작인 61 직전)에 둔다.
 *
 * <p>추정 인원({@code populationMin}/{@code populationMax})은 쓰지 않는다. 장소마다
 * 면적이 달라 같은 인원이라도 위험도가 전혀 다르고, 서울시가 이미 면적을 고려해
 * 4단계로 정규화했다. 인원은 화면 표시용이다.
 */
public final class PublicCongestionScore {

	public static final double RELAXED_SCORE = 10.0;
	public static final double NORMAL_SCORE = 30.0;
	public static final double SLIGHTLY_CROWDED_SCORE = 45.0;
	public static final double CROWDED_SCORE = 60.0;

	/** 공공데이터 단독 관측의 신뢰도. 정기 관측이지만 넓은 지역 평균이다. */
	public static final double PUBLIC_DATA_CONFIDENCE = 0.5;

	private PublicCongestionScore() {
	}

	public static double of(CongestionLevel level) {
		return switch (level) {
			case RELAXED -> RELAXED_SCORE;
			case NORMAL -> NORMAL_SCORE;
			case SLIGHTLY_CROWDED -> SLIGHTLY_CROWDED_SCORE;
			case CROWDED -> CROWDED_SCORE;
		};
	}
}
