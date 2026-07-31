package com.ojimasam.safy.publicdata.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class DisasterMessageCollectionRequestTest {
	/**
	 * 컨테이너 TZ 가 UTC 라서 시스템 기본 존으로 계산하면 KST 09시 이전에 날짜가 하루 밀린다.
	 * 이 시각(KST 07-31 00:30)의 어제는 07-30 이고, UTC 기준으로 계산하면 07-29 가 된다.
	 */
	@Test
	void resolvesYesterdayInKoreanTime() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-30T15:30:00Z"), ZoneOffset.UTC);

		assertThat(DisasterMessageCollectionRequest.lookingBack(clock, 1).startDate())
				.isEqualTo("2026-07-30");
	}

	/** 조회 창을 늘리면 시작일만 앞으로 밀린다(원본 crtDt 는 조회 시작일자다). */
	@Test
	void widensWindowByLookbackDays() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-31T04:00:00Z"), ZoneOffset.UTC);

		assertThat(DisasterMessageCollectionRequest.lookingBack(clock, 0).startDate())
				.isEqualTo("2026-07-31");
		assertThat(DisasterMessageCollectionRequest.lookingBack(clock, 3).startDate())
				.isEqualTo("2026-07-28");
	}
}
