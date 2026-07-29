package com.ojimasam.safy.administrativearea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ojimasam.safy.administrativearea.application.AddressResolutionException;

class KakaoAddressResolverTest {
	@Test
	void prefersRoadAddress() {
		var result = resolveWith("""
				{
				  "documents": [
				    {
				      "road_address": { "address_name": "서울 강남구 테헤란로 152" },
				      "address": { "address_name": "서울 강남구 역삼동 737" }
				    }
				  ]
				}
				""");

		assertThat(result).contains("서울 강남구 테헤란로 152");
	}

	/** 실제로 도로명 주소가 없는 좌표가 존재한다. (127.04, 37.5018 → 역삼동 804) */
	@Test
	void fallsBackToJibunAddressWhenRoadAddressIsNull() {
		var result = resolveWith("""
				{
				  "documents": [
				    {
				      "road_address": null,
				      "address": { "address_name": "서울 강남구 역삼동 804" }
				    }
				  ]
				}
				""");

		assertThat(result).contains("서울 강남구 역삼동 804");
	}

	@Test
	void returnsEmptyWhenNoDocumentMatches() {
		assertThat(resolveWith("{ \"documents\": [] }")).isEmpty();
	}

	@Test
	void rejectsCallWhenRestApiKeyIsMissing() {
		KakaoLocalProperties properties = properties("");
		KakaoAddressResolver resolver = new KakaoAddressResolver(
				RestClient.builder().baseUrl(properties.baseUrl().toString()).build(),
				properties
		);

		assertThatThrownBy(() -> resolver.resolve(new BigDecimal("37.5"), new BigDecimal("127.0")))
				.isInstanceOf(AddressResolutionException.class);
	}

	private java.util.Optional<String> resolveWith(String responseBody) {
		KakaoLocalProperties properties = properties("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(
				"https://dapi.kakao.com/v2/local/geo/coord2address.json?x=127.0400000&y=37.5018000"
		)).andExpect(header("Authorization", "KakaoAK test-key"))
				.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
		KakaoAddressResolver resolver = new KakaoAddressResolver(builder.build(), properties);

		var result = resolver.resolve(new BigDecimal("37.5018000"), new BigDecimal("127.0400000"));

		server.verify();
		return result;
	}

	private KakaoLocalProperties properties(String key) {
		return new KakaoLocalProperties(
				key,
				URI.create("https://dapi.kakao.com"),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1)
		);
	}
}
