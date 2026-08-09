package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ojimasam.safy.administrativearea.application.AddressResolutionException;
import com.ojimasam.safy.administrativearea.application.AddressResolver;

@Component
public class KakaoAddressResolver implements AddressResolver {
	private final RestClient restClient;
	private final KakaoLocalProperties properties;

	public KakaoAddressResolver(
			@Qualifier("kakaoLocalRestClient") RestClient restClient,
			KakaoLocalProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public Optional<String> resolve(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");
		if (properties.restApiKey().isBlank()) {
			throw new AddressResolutionException("카카오 로컬 REST API 키가 설정되지 않았습니다.");
		}
		try {
			KakaoAddressResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/v2/local/geo/coord2address.json")
							.queryParam("x", longitude.toPlainString())
							.queryParam("y", latitude.toPlainString())
							.build())
					.header("Authorization", "KakaoAK " + properties.restApiKey())
					.retrieve()
					.body(KakaoAddressResponse.class);

			if (response == null || response.documents() == null) return Optional.empty();
			return response.documents().stream()
					.map(KakaoAddressResolver::addressName)
					.flatMap(Optional::stream)
					.findFirst();
		} catch (RestClientException exception) {
			throw new AddressResolutionException("카카오 주소 조회에 실패했습니다.", exception);
		}
	}

	/** 도로명 주소가 없는 좌표가 실제로 존재하므로 지번 주소로 보완한다. */
	private static Optional<String> addressName(KakaoAddressDocument document) {
		return Stream.of(document.roadAddress(), document.jibunAddress())
				.filter(name -> name != null && name.addressName() != null && !name.addressName().isBlank())
				.map(KakaoAddressName::addressName)
				.findFirst();
	}

	record KakaoAddressResponse(List<KakaoAddressDocument> documents) {
	}

	record KakaoAddressDocument(
			@JsonProperty("road_address") KakaoAddressName roadAddress,
			@JsonProperty("address") KakaoAddressName jibunAddress
	) {
	}

	record KakaoAddressName(@JsonProperty("address_name") String addressName) {
	}
}
