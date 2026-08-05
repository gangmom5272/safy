package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ojimasam.safy.administrativearea.application.KeywordPlace;
import com.ojimasam.safy.administrativearea.application.PlaceKeywordSearcher;

/**
 * 카카오 로컬 키워드 검색. 좌표→자치구 해석과 같은 호스트·같은 REST 키를 쓴다.
 *
 * <p>서울 밖 결과는 버린다. 상황 요약이 서울시 실시간 도시데이터와 서울 제보에만 근거하므로,
 * 부산 장소를 고르면 세 항목이 전부 비어 "검색은 됐는데 아무것도 안 나오는" 화면이 된다.
 * 결과를 아예 보여주지 않는 편이 낫다.
 */
@Component
public class KakaoPlaceKeywordSearcher implements PlaceKeywordSearcher {
	private static final Logger log = LoggerFactory.getLogger(KakaoPlaceKeywordSearcher.class);

	/** 카카오가 지역명을 앞에 붙여 주는 주소에서 서울을 가려낸다. */
	private static final String SEOUL_PREFIX = "서울";

	private final RestClient restClient;
	private final KakaoLocalProperties properties;

	public KakaoPlaceKeywordSearcher(
			@Qualifier("kakaoLocalRestClient") RestClient restClient,
			KakaoLocalProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public List<KeywordPlace> search(String keyword, int limit) {
		if (keyword == null || keyword.isBlank() || limit < 1) {
			return List.of();
		}
		if (properties.restApiKey().isBlank()) {
			// 키가 없는 환경(로컬·CI)에서는 자치구·121장소 검색만 동작하면 된다.
			return List.of();
		}

		try {
			KakaoKeywordResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/v2/local/search/keyword.json")
							.queryParam("query", keyword)
							// 카카오 상한은 15다. 서울 밖 결과를 걸러내면 남는 수가 줄어드니
							// 필요한 만큼보다 넉넉히 받아 둔다.
							.queryParam("size", Math.min(limit * 2, 15))
							.build())
					.header("Authorization", "KakaoAK " + properties.restApiKey())
					.retrieve()
					.body(KakaoKeywordResponse.class);

			if (response == null || response.documents() == null) {
				return List.of();
			}

			return response.documents().stream()
					.map(KakaoPlaceKeywordSearcher::toKeywordPlace)
					.filter(place -> place != null)
					.filter(place -> place.address() != null && place.address().startsWith(SEOUL_PREFIX))
					.limit(limit)
					.toList();
		} catch (RestClientException exception) {
			// 외부 장애가 검색 전체를 막으면 안 된다. 자치구·121장소 결과는 그대로 나간다.
			log.warn("카카오 키워드 검색에 실패했습니다. keyword={}", keyword, exception);
			return List.of();
		}
	}

	/** 좌표가 없거나 숫자가 아니면 상황 요약을 계산할 수 없어 버린다. */
	private static KeywordPlace toKeywordPlace(KakaoKeywordDocument document) {
		if (document.placeName() == null || document.placeName().isBlank()) {
			return null;
		}

		BigDecimal longitude = parseCoordinate(document.x());
		BigDecimal latitude = parseCoordinate(document.y());
		if (latitude == null || longitude == null) {
			return null;
		}

		String address = document.roadAddressName() != null && !document.roadAddressName().isBlank()
				? document.roadAddressName()
				: document.addressName();

		return new KeywordPlace(document.placeName(), address, latitude, longitude);
	}

	private static BigDecimal parseCoordinate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	record KakaoKeywordResponse(List<KakaoKeywordDocument> documents) {
	}

	record KakaoKeywordDocument(
			@JsonProperty("place_name") String placeName,
			@JsonProperty("road_address_name") String roadAddressName,
			@JsonProperty("address_name") String addressName,
			@JsonProperty("x") String x,
			@JsonProperty("y") String y
	) {
	}
}
