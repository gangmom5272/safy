package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

@Component
public class KakaoAdministrativeAreaResolver implements AdministrativeAreaResolver {
	private static final String SEOUL = "서울특별시";
	private static final String LEGAL_REGION_TYPE = "B";

	private final RestClient restClient;
	private final KakaoLocalProperties properties;

	public KakaoAdministrativeAreaResolver(
			@Qualifier("kakaoLocalRestClient") RestClient restClient,
			KakaoLocalProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public Optional<ResolvedAdministrativeArea> resolve(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		if (properties.restApiKey().isBlank()) {
			throw new AdministrativeAreaResolutionException("카카오 로컬 REST API 키가 설정되지 않았습니다.");
		}
		try {
			KakaoRegionResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/v2/local/geo/coord2regioncode.json")
							.queryParam("x", longitude.toPlainString())
							.queryParam("y", latitude.toPlainString())
							.build())
					.header("Authorization", "KakaoAK " + properties.restApiKey())
					.retrieve()
					.body(KakaoRegionResponse.class);

			if (response == null || response.documents() == null) return Optional.empty();
			return response.documents().stream()
					.filter(document -> LEGAL_REGION_TYPE.equals(document.regionType()))
					.filter(document -> SEOUL.equals(document.region1DepthName()))
					.filter(document -> document.code() != null && document.code().length() >= 5)
					.filter(document -> document.region2DepthName() != null && !document.region2DepthName().isBlank())
					.findFirst()
					.map(document -> new ResolvedAdministrativeArea(
							normalizeSigunguCode(document.code()),
							document.region2DepthName()
					));
		} catch (RestClientException exception) {
			throw new AdministrativeAreaResolutionException("카카오 행정구역 조회에 실패했습니다.", exception);
		}
	}

	private String normalizeSigunguCode(String regionCode) {
		return regionCode.substring(0, 5) + "00000";
	}

	record KakaoRegionResponse(List<KakaoRegionDocument> documents) {
	}

	record KakaoRegionDocument(
			@JsonProperty("region_type") String regionType,
			String code,
			@JsonProperty("region_1depth_name") String region1DepthName,
			@JsonProperty("region_2depth_name") String region2DepthName
	) {
	}
}
