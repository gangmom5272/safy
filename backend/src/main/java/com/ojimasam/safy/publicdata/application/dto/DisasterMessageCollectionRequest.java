package com.ojimasam.safy.publicdata.application.dto;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 재난문자 조회 시작일. FastAPI 가 이 값을 원본 API 의 {@code crtDt} 로 넘긴다.
 *
 * 왜 시작일을 반드시 실어 보내는가:
 *   주지 않으면 원본 API 가 가장 오래된 구간을 돌려주므로 최신 문자를 한 건도 받지 못한다.
 *   {@code crtDt} 는 조회 <b>시작</b>일자라서 어제 날짜를 넣으면 어제부터 최신까지 한 번에 온다.
 *   2분 주기에 하루 창이면 재배포·장애 복구까지 흡수한다.
 *
 * 날짜를 String 으로 담는 이유:
 *   이 요청을 보내는 RestClient 는 Spring Boot 가 구성한 ObjectMapper 를 쓰지 않아
 *   LocalDate 직렬화 형식이 보장되지 않는다. FastAPI 가 파싱하는 yyyy-MM-dd 로 직접 고정한다.
 *
 * 조회 창이 넓어질수록 1회 응답 상한 1,000건에 가까워진다는 점은 주의해야 한다.
 * 원본 응답에는 정렬이 없어 상한에 걸리면 어떤 건이 빠질지 예측할 수 없다
 * (실측 기준 이틀치 348건이라 평상시에는 여유가 있다).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisasterMessageCollectionRequest(
		String startDate
) {
	/**
	 * crtDt 는 KST 기준 날짜다. 컨테이너 TZ 가 UTC 라서 시스템 기본 존을 쓰면
	 * KST 09시 이전에 하루 이른 날짜가 나와 조회 창이 필요 이상으로 벌어진다.
	 */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static DisasterMessageCollectionRequest lookingBack(int days) {
		return lookingBack(Clock.systemUTC(), days);
	}

	static DisasterMessageCollectionRequest lookingBack(Clock clock, int days) {
		LocalDate startDate = LocalDate.ofInstant(clock.instant(), KST).minusDays(days);
		return new DisasterMessageCollectionRequest(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
	}
}
