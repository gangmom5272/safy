package com.ojimasam.safy.place.domain.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CongestionLevelTest {
	@Test
	void readsSeoulKoreanLabels() {
		assertThat(CongestionLevel.from("여유")).contains(CongestionLevel.RELAXED);
		assertThat(CongestionLevel.from("보통")).contains(CongestionLevel.NORMAL);
		assertThat(CongestionLevel.from("약간 붐빔")).contains(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(CongestionLevel.from("붐빔")).contains(CongestionLevel.CROWDED);
	}

	@Test
	void readsEnumNames() {
		assertThat(CongestionLevel.from("SLIGHTLY_CROWDED")).contains(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(CongestionLevel.from("crowded")).contains(CongestionLevel.CROWDED);
	}

	/** 상류에서 공백이 제거되거나 늘어나도 같은 단계로 인식해야 한다. */
	@Test
	void toleratesWhitespaceDifferences() {
		assertThat(CongestionLevel.from("약간붐빔")).contains(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(CongestionLevel.from("약간  붐빔")).contains(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(CongestionLevel.from("  붐빔  ")).contains(CongestionLevel.CROWDED);
	}

	@Test
	void rejectsUnknownValues() {
		assertThat(CongestionLevel.from(null)).isEmpty();
		assertThat(CongestionLevel.from("   ")).isEmpty();
		assertThat(CongestionLevel.from("매우 한가함")).isEmpty();
	}
}
