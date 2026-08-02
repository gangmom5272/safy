package com.ojimasam.safy.analysis.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ojimasam.safy.place.domain.type.CongestionLevel;

class PublicCongestionScoreTest {
	@ParameterizedTest(name = "{0} → {1}점")
	@CsvSource({
			"RELAXED, 10.0",
			"NORMAL, 30.0",
			"SLIGHTLY_CROWDED, 45.0",
			"CROWDED, 60.0"
	})
	@DisplayName("혼잡도 4단계를 명세 점수로 바꾼다")
	void convertsEveryLevel(CongestionLevel level, double expected) {
		assertThat(PublicCongestionScore.of(level)).isEqualTo(expected);
	}

	@Test
	@DisplayName("공공데이터만으로는 MEDIUM 을 넘지 않는다")
	void neverExceedsMediumOnPublicDataAlone() {
		// 서울시 척도는 보행 편의성 기준이라 최고 등급도 위험을 뜻하지 않는다.
		// 이 경계가 무너지면 제보 없이도 지도가 붉어진다.
		for (CongestionLevel level : CongestionLevel.values()) {
			assertThat(RiskLevel.fromScore(PublicCongestionScore.of(level)))
					.isLessThanOrEqualTo(RiskLevel.MEDIUM);
		}

		assertThat(RiskLevel.fromScore(PublicCongestionScore.CROWDED_SCORE)).isEqualTo(RiskLevel.MEDIUM);
		// 1점만 높아도 HIGH 로 넘어간다. 60 은 우연이 아니라 경계값이다.
		assertThat(RiskLevel.fromScore(PublicCongestionScore.CROWDED_SCORE + 1)).isEqualTo(RiskLevel.HIGH);
	}

	@Test
	@DisplayName("혼잡할수록 점수가 높다")
	void increasesWithCongestion() {
		CongestionLevel[] levels = CongestionLevel.values();

		for (int index = 1; index < levels.length; index++) {
			assertThat(PublicCongestionScore.of(levels[index]))
					.isGreaterThan(PublicCongestionScore.of(levels[index - 1]));
		}
	}
}
