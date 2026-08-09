package com.ojimasam.safy.publicdata.application.dto;

import java.time.Instant;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * FastAPI 가 공공데이터 원본 API 를 호출한 실행 메타데이터. 모든 응답에 동봉된다.
 * externalRunId 는 같은 실행을 두 번 적재하지 않기 위한 키이므로 반드시 채워져야 한다.
 *
 * errorDetails 를 JsonNode 로 받는 이유:
 *   구조화된 에러는 JSON 객체로 오는 것이 자연스럽고 저장 컬럼도 jsonb 다.
 *   String 으로 두면 객체가 왔을 때 역직렬화가 실패해 응답 전체가 버려진다.
 *   JsonNode 는 객체·배열·문자열을 모두 받아들인다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CollectionRunPayload(
		String externalRunId,
		String source,
		Instant startedAt,
		Instant finishedAt,
		String status,
		Integer successCount,
		Integer failureCount,
		String errorCode,
		String errorMessage,
		JsonNode errorDetails
) {
	/** jsonb 컬럼에 넣을 JSON 문자열. 값이 없으면 null 이다. */
	public String errorDetailsAsJson() {
		if (errorDetails == null || errorDetails.isNull()) {
			return null;
		}
		return errorDetails.toString();
	}
}
