package com.ojimasam.safy.publicdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ojimasam.safy.publicdata.application.dto.CityCongestionResponse;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageCollectionRequest;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

class PublicDataClientTest {
	private static final String BASE_URL = "http://localhost:8000";
	private static final String CONGESTION_URL = BASE_URL + "/api/v1/collections/seoul-congestions";
	private static final List<String> AREA_CODES = List.of("POI001", "POI002");

	@Test
	void sendsAreaCodesAndReadsCongestionResponse() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(CONGESTION_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				// 장소 목록의 단일 진실 원천이 places 이므로 코드가 그대로 실려 나가야 한다.
				.andExpect(jsonPath("$.area_codes[0]").value("POI001"))
				.andExpect(jsonPath("$.area_codes[1]").value("POI002"))
				.andRespond(withSuccess("""
						{
						  "collection_run": {
						    "external_run_id": "run-1",
						    "source": "seoul-citydata",
						    "started_at": "2026-07-29T13:00:00+09:00",
						    "finished_at": "2026-07-29T13:00:40+09:00",
						    "status": "PARTIAL_SUCCESS",
						    "success_count": 120,
						    "failure_count": 1,
						    "error_code": "UPSTREAM_TIMEOUT",
						    "error_details": {"failedAreaCodes": ["POI077"]}
						  },
						  "items": [
						    {
						      "area_code": "POI001",
						      "area_name": "강남 MICE 관광특구",
						      "congestion_level": "약간 붐빔",
						      "congestion_message": "사람들이 몰려있을 가능성이 있어요.",
						      "population_min": 12000,
						      "population_max": 14000,
						      "observed_at": "2026-07-29T13:00:00+09:00"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		Optional<CityCongestionResponse> response =
				new PublicDataClient(builder.build()).fetchCityCongestion(AREA_CODES);

		server.verify();
		assertThat(response).isPresent();
		assertThat(response.get().collectionRun().externalRunId()).isEqualTo("run-1");
		// 121곳 팬아웃 중 일부 실패가 정상 시나리오이므로 부분 성공이 그대로 읽혀야 한다.
		assertThat(response.get().collectionRun().status()).isEqualTo("PARTIAL_SUCCESS");
		assertThat(response.get().collectionRun().failureCount()).isEqualTo(1);
		// error_details 는 JSON 객체로 오며, String 으로 받으면 응답 전체가 버려진다.
		assertThat(response.get().collectionRun().errorDetailsAsJson()).contains("POI077");
		assertThat(response.get().safeItems()).hasSize(1);
		assertThat(response.get().safeItems().getFirst().observedAt())
				.isEqualTo(Instant.parse("2026-07-29T04:00:00Z"));
	}

	/** 계약에 없는 필드가 추가되어도 나머지는 읽혀야 한다. */
	@Test
	void ignoresUnknownFields() {
		Optional<CityCongestionResponse> response = respondWith("""
				{
				  "collection_run": {
				    "external_run_id": "run-2",
				    "source": "seoul-citydata",
				    "started_at": "2026-07-29T13:00:00+09:00",
				    "status": "SUCCESS",
				    "brand_new_field": "무시되어야 함"
				  },
				  "items": [],
				  "another_new_field": 123
				}
				""");

		assertThat(response).isPresent();
		assertThat(response.get().collectionRun().externalRunId()).isEqualTo("run-2");
		assertThat(response.get().safeItems()).isEmpty();
	}

	/** 응답 형식이 깨져도 다음 주기에 다시 시도하면 되므로 예외를 밖으로 내보내지 않는다. */
	@Test
	void returnsEmptyOnUnreadableBody() {
		assertThat(respondWith("{ \"items\": \"배열이 아님\" }")).isEmpty();
	}

	@Test
	void returnsEmptyOnServerError() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(CONGESTION_URL)).andRespond(withServerError());

		assertThat(new PublicDataClient(builder.build()).fetchCityCongestion(AREA_CODES)).isEmpty();
		server.verify();
	}

	/**
	 * FastAPI 는 자체 제한 시간(100초)을 넘겨도 HTTP 200 에 status=FAILED 와 빈 items 를 담아 응답한다.
	 * 통신 오류가 아니므로 응답 자체는 읽히고, 상태와 실패 내역이 그대로 전달되어야 한다.
	 */
	@Test
	void readsFailedRunReturnedWithHttp200() {
		Optional<CityCongestionResponse> response = respondWith("""
				{
				  "collection_run": {
				    "external_run_id": "seoul-citydata-ppltn:0f7c2a1e-8a1b-4c2f-9d3e-5b6a7c8d9e01",
				    "source": "seoul-citydata-ppltn",
				    "started_at": "2026-07-30T04:00:00Z",
				    "finished_at": "2026-07-30T04:01:40Z",
				    "status": "FAILED",
				    "success_count": 0,
				    "failure_count": 121,
				    "error_code": "COLLECTION_TIMEOUT",
				    "error_message": "혼잡도 수집 제한 시간을 초과했습니다."
				  },
				  "items": [],
				  "failures": []
				}
				""");

		assertThat(response).isPresent();
		// 'FAILED' 는 FastAPI 계약의 값이다. 'FAILURE' 로 두면 파싱에 실패해 성공으로 기록된다.
		assertThat(CollectionStatus.from(response.get().collectionRun().status()))
				.contains(CollectionStatus.FAILED);
		assertThat(response.get().collectionRun().failureCount()).isEqualTo(121);
		assertThat(response.get().safeItems()).isEmpty();
	}

	/** 지역별 실패 내역은 error_details 안의 JSON 객체로 온다. */
	@Test
	void readsPerAreaFailuresFromErrorDetails() {
		Optional<CityCongestionResponse> response = respondWith("""
				{
				  "collection_run": {
				    "external_run_id": "seoul-citydata-ppltn:1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
				    "source": "seoul-citydata-ppltn",
				    "started_at": "2026-07-30T04:00:00Z",
				    "finished_at": "2026-07-30T04:00:35Z",
				    "status": "PARTIAL_SUCCESS",
				    "success_count": 120,
				    "failure_count": 1,
				    "error_details": {
				      "failures": [
				        {"area": "POI077", "error": "서울 실시간 도시데이터 API 요청 시간이 초과되었습니다."}
				      ]
				    }
				  },
				  "items": [],
				  "failures": [
				    {"area": "POI077", "error": "서울 실시간 도시데이터 API 요청 시간이 초과되었습니다."}
				  ]
				}
				""");

		assertThat(response).isPresent();
		assertThat(CollectionStatus.from(response.get().collectionRun().status()))
				.contains(CollectionStatus.PARTIAL_SUCCESS);
		assertThat(response.get().collectionRun().errorDetailsAsJson()).contains("POI077");
	}

	/**
	 * 재난문자는 장소 코드 목록 없이 POST 하지만, 조회 시작일은 반드시 실려 나가야 한다.
	 * 빠지면 FastAPI 가 crtDt 를 붙이지 않아 가장 오래된 구간만 받는다.
	 */
	@Test
	void sendsStartDateWhenCollectingDisasterMessages() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(BASE_URL + "/api/v1/collections/disaster-messages"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				// FastAPI 는 date 로 파싱하므로 yyyy-MM-dd 문자열이어야 한다.
				.andExpect(jsonPath("$.start_date").value("2026-07-30"))
				.andRespond(withSuccess("""
						{
						  "collection_run": {
						    "external_run_id": "run-disaster-1",
						    "source": "safetydata.go.kr",
						    "started_at": "2026-07-29T13:00:00+09:00",
						    "status": "SUCCESS"
						  },
						  "items": [
						    {
						      "external_id": "SN-20260729-0001",
						      "sent_at": "2026-07-29T13:01:00+09:00",
						      "message": "강남구 일대 인구 밀집으로 통행에 주의하십시오.",
						      "regions": ["서울특별시 강남구", "서울특별시 서초구"],
						      "emergency_level": "안전안내",
						      "disaster_type": "인구밀집"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		Optional<DisasterMessageResponse> response = new PublicDataClient(builder.build())
				.fetchDisasterMessages(new DisasterMessageCollectionRequest("2026-07-30"));

		server.verify();
		assertThat(response).isPresent();
		assertThat(response.get().safeItems()).hasSize(1);
		assertThat(response.get().safeItems().getFirst().regions())
				.containsExactly("서울특별시 강남구", "서울특별시 서초구");
		assertThat(response.get().safeItems().getFirst().sentAt())
				.isEqualTo(Instant.parse("2026-07-29T04:01:00Z"));
	}

	/** 수집 대상이 없으면 FastAPI 를 호출하지 않는다. */
	@Test
	void skipsRequestWhenNoAreaCodes() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PublicDataClient client = new PublicDataClient(builder.build());

		assertThat(client.fetchCityCongestion(List.of())).isEmpty();
		assertThat(client.fetchCityCongestion(null)).isEmpty();

		server.verify();
	}

	private Optional<CityCongestionResponse> respondWith(String responseBody) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(CONGESTION_URL))
				.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		Optional<CityCongestionResponse> response = new PublicDataClient(builder.build())
				.fetchCityCongestion(AREA_CODES);

		server.verify();
		return response;
	}
}
