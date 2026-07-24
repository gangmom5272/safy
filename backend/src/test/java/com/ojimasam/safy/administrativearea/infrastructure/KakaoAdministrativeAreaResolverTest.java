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

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;

class KakaoAdministrativeAreaResolverTest {
	@Test
	void resolvesLegalDongCodeToSigunguCode() {
		KakaoLocalProperties properties = properties("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(
				"https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=127.0276000&y=37.4979000"
		)).andExpect(header("Authorization", "KakaoAK test-key"))
				.andRespond(withSuccess("""
						{
						  "documents": [
						    {
						      "region_type": "B",
						      "code": "1168010100",
						      "region_1depth_name": "서울특별시",
						      "region_2depth_name": "강남구"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));
		KakaoAdministrativeAreaResolver resolver = new KakaoAdministrativeAreaResolver(builder.build(), properties);

		var result = resolver.resolve(new BigDecimal("37.4979000"), new BigDecimal("127.0276000"));

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().code()).isEqualTo("1168000000");
		assertThat(result.orElseThrow().name()).isEqualTo("강남구");
		server.verify();
	}

	@Test
	void rejectsCallWhenRestApiKeyIsMissing() {
		KakaoLocalProperties properties = properties("");
		KakaoAdministrativeAreaResolver resolver = new KakaoAdministrativeAreaResolver(
				RestClient.builder().baseUrl(properties.baseUrl().toString()).build(),
				properties
		);

		assertThatThrownBy(() -> resolver.resolve(new BigDecimal("37.5"), new BigDecimal("127.0")))
				.isInstanceOf(AdministrativeAreaResolutionException.class);
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
