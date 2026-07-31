package com.ojimasam.safy.publicdata.infrastructure;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ojimasam.safy.publicdata.application.dto.AreaCodesRequest;
import com.ojimasam.safy.publicdata.application.dto.CityCongestionResponse;
import com.ojimasam.safy.publicdata.application.dto.CulturalEventResponse;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageCollectionRequest;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;

/**
 * FastAPI 데이터 서버에서 수집 결과를 가져온다.
 *
 * 혼잡도·행사는 우리가 보낸 장소 코드 목록을 FastAPI 가 나눠 병렬 호출한 뒤 하나로 합쳐서 응답한다.
 * 즉 이 호출이 곧 수집을 트리거하므로 응답까지 수십 초가 걸린다(read-timeout 을 넉넉히 잡아야 한다).
 * 재난문자는 장소 기반이 아니라 목록이 필요 없다.
 *
 * 응답 형식이 바뀌면 이 클래스와 application/dto 만 수정하면 되고 도메인은 영향받지 않는다.
 */
@Component
public class PublicDataClient {
	private static final Logger log = LoggerFactory.getLogger(PublicDataClient.class);

	private static final String CITY_CONGESTION_PATH = "/api/v1/collections/seoul-congestions";
	private static final String CULTURAL_EVENT_PATH = "/api/v1/collections/seoul-events";
	private static final String DISASTER_MESSAGE_PATH = "/api/v1/collections/disaster-messages";

	private final RestClient restClient;

	public PublicDataClient(@Qualifier("publicDataRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	public Optional<CityCongestionResponse> fetchCityCongestion(List<String> areaCodes) {
		return collect(CITY_CONGESTION_PATH, areaCodes, CityCongestionResponse.class);
	}

	public Optional<CulturalEventResponse> fetchCulturalEvents(List<String> areaCodes) {
		return collect(CULTURAL_EVENT_PATH, areaCodes, CulturalEventResponse.class);
	}

	/**
	 * 재난문자는 장소 기반이 아니라 보낼 코드 목록이 없다.
	 * 다만 조회 시작일은 반드시 실어 보낸다. 생략하면 FastAPI 가 crtDt 를 붙이지 않고,
	 * 원본 API 가 가장 오래된 구간을 돌려주므로 최신 문자를 한 건도 받지 못한다.
	 * 페이지·건수·지역은 FastAPI 기본값(1페이지, 1,000건, 전체 지역)이 그대로 필요한 값이다.
	 */
	public Optional<DisasterMessageResponse> fetchDisasterMessages(DisasterMessageCollectionRequest request) {
		try {
			return Optional.ofNullable(
					restClient.post()
							.uri(DISASTER_MESSAGE_PATH)
							.body(request)
							.retrieve()
							.body(DisasterMessageResponse.class)
			);
		} catch (RestClientException exception) {
			log.warn("재난문자 수집 요청에 실패했습니다. path={}", DISASTER_MESSAGE_PATH, exception);
			return Optional.empty();
		}
	}

	/**
	 * 수집은 다음 주기에 다시 시도하면 되므로, 통신 실패를 예외로 올리지 않고 빈 값으로 돌린다.
	 */
	private <T> Optional<T> collect(String path, List<String> areaCodes, Class<T> responseType) {
		if (areaCodes == null || areaCodes.isEmpty()) {
			log.warn("수집 대상 장소가 없어 요청을 건너뜁니다. path={}", path);
			return Optional.empty();
		}

		try {
			return Optional.ofNullable(
					restClient.post()
							.uri(path)
							.body(new AreaCodesRequest(areaCodes))
							.retrieve()
							.body(responseType)
			);
		} catch (RestClientException exception) {
			log.warn("공공데이터 수집 요청에 실패했습니다. path={}, 장소 수={}",
					path, areaCodes.size(), exception);
			return Optional.empty();
		}
	}
}
