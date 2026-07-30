package com.ojimasam.safy.publicdata.infrastructure;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PublicDataClientConfig {
	@Bean
	@Qualifier("publicDataRestClient")
	RestClient publicDataRestClient(PublicDataProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				// JDK HttpClient 는 버전을 명시하지 않으면 HTTP/2 를 우선 시도한다.
				// FastAPI(uvicorn 의 h11)는 평문 연결에서 HTTP/2(h2c) 업그레이드를 지원하지 않아,
				// 바디가 있는 POST 요청에서 "Invalid HTTP request received." 400 오류로 거부된다.
				// (2026-07-30 실측: 바디 없는 POST 는 통과하고, 바디 있는 POST 만 이 오류가 났다.)
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.build();
	}
}
