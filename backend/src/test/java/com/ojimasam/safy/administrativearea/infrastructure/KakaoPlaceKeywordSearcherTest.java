package com.ojimasam.safy.administrativearea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

import com.ojimasam.safy.administrativearea.application.KeywordPlace;

class KakaoPlaceKeywordSearcherTest {
	@Test
	void readsNameAddressAndCoordinates() {
		List<KeywordPlace> result = searchWith("""
				{
				  "documents": [
				    {
				      "place_name": "멀티캠퍼스 역삼지점",
				      "road_address_name": "서울 강남구 테헤란로 212",
				      "address_name": "서울 강남구 역삼동 718",
				      "x": "127.0396",
				      "y": "37.5013"
				    }
				  ]
				}
				""");

		assertThat(result).hasSize(1);
		KeywordPlace place = result.get(0);
		assertThat(place.name()).isEqualTo("멀티캠퍼스 역삼지점");
		assertThat(place.address()).isEqualTo("서울 강남구 테헤란로 212");
		assertThat(place.latitude()).isEqualByComparingTo("37.5013");
		assertThat(place.longitude()).isEqualByComparingTo("127.0396");
	}

	/** 도로명 주소가 없는 장소가 실제로 있다. 지번으로 보완한다(KakaoAddressResolver 와 같은 규칙). */
	@Test
	void fallsBackToJibunAddress() {
		List<KeywordPlace> result = searchWith("""
				{
				  "documents": [
				    {
				      "place_name": "어떤 가게",
				      "road_address_name": "",
				      "address_name": "서울 강남구 역삼동 718",
				      "x": "127.0396",
				      "y": "37.5013"
				    }
				  ]
				}
				""");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).address()).isEqualTo("서울 강남구 역삼동 718");
	}

	/**
	 * 서울 밖 결과는 버린다. 상황 요약이 서울시 실시간 도시데이터와 서울 제보에만 근거해서,
	 * 부산 장소를 고르면 세 항목이 전부 비어 "검색은 됐는데 아무것도 안 나오는" 화면이 된다.
	 */
	@Test
	void dropsResultsOutsideSeoul() {
		List<KeywordPlace> result = searchWith("""
				{
				  "documents": [
				    {
				      "place_name": "부산 멀티캠퍼스",
				      "road_address_name": "부산 해운대구 센텀중앙로 90",
				      "address_name": "부산 해운대구 우동 1495",
				      "x": "129.1300",
				      "y": "35.1700"
				    },
				    {
				      "place_name": "멀티캠퍼스 역삼지점",
				      "road_address_name": "서울 강남구 테헤란로 212",
				      "address_name": "서울 강남구 역삼동 718",
				      "x": "127.0396",
				      "y": "37.5013"
				    }
				  ]
				}
				""");

		assertThat(result).extracting(KeywordPlace::name).containsExactly("멀티캠퍼스 역삼지점");
	}

	/** 좌표가 없으면 상황 요약을 계산할 수 없어 버린다. */
	@Test
	void dropsResultsWithoutUsableCoordinates() {
		List<KeywordPlace> result = searchWith("""
				{
				  "documents": [
				    {
				      "place_name": "좌표 없는 곳",
				      "road_address_name": "서울 강남구 테헤란로 212",
				      "x": "",
				      "y": null
				    }
				  ]
				}
				""");

		assertThat(result).isEmpty();
	}

	/** 외부 장애가 검색 전체를 막으면 안 된다 — 자치구·121장소 결과는 그대로 나가야 한다. */
	@Test
	void returnsEmptyInsteadOfThrowingWhenKakaoFails() {
		KakaoLocalProperties properties = properties("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withServerError());

		List<KeywordPlace> result = new KakaoPlaceKeywordSearcher(builder.build(), properties)
				.search("멀티캠퍼스", 5);

		assertThat(result).isEmpty();
	}

	/** 키가 없는 환경(로컬·CI)에서는 호출하지 않고 조용히 빈 목록을 준다. */
	@Test
	void returnsEmptyWithoutCallingWhenApiKeyIsMissing() {
		KakaoLocalProperties properties = properties("");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		List<KeywordPlace> result = new KakaoPlaceKeywordSearcher(builder.build(), properties)
				.search("멀티캠퍼스", 5);

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	void returnsEmptyForBlankKeyword() {
		KakaoLocalProperties properties = properties("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		assertThat(new KakaoPlaceKeywordSearcher(builder.build(), properties).search(" ", 5)).isEmpty();
		server.verify();
	}

	private List<KeywordPlace> searchWith(String responseBody) {
		KakaoLocalProperties properties = properties("test-key");
		RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(MockRestRequestMatchers.anything())
				.andExpect(header("Authorization", "KakaoAK test-key"))
				.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		List<KeywordPlace> result = new KakaoPlaceKeywordSearcher(builder.build(), properties)
				.search("멀티캠퍼스", 5);

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
