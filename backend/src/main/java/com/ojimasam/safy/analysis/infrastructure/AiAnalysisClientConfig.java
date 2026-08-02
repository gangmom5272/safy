package com.ojimasam.safy.analysis.infrastructure;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiAnalysisClientConfig {
	@Bean
	@Qualifier("aiAnalysisRestClient")
	RestClient aiAnalysisRestClient(AiServerProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
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
