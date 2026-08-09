package com.ojimasam.safy.administrativearea.infrastructure;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoLocalClientConfig {
	@Bean
	@Qualifier("kakaoLocalRestClient")
	RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.build();
	}
}
