package com.ojimasam.safy.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {
	@Bean
	public MinioClient minioClient(
			@Value("${minio.endpoint}") String endpoint,
			@Value("${minio.access-key}") String accessKey,
			@Value("${minio.secret-key}") String secretKey
	) {
		return buildClient(endpoint, accessKey, secretKey);
	}

	@Bean
	@Qualifier("minioPresignClient")
	public MinioClient minioPresignClient(
			@Value("${minio.public-endpoint:${minio.endpoint}}") String endpoint,
			@Value("${minio.access-key}") String accessKey,
			@Value("${minio.secret-key}") String secretKey
	) {
		return buildClient(endpoint, accessKey, secretKey);
	}

	private MinioClient buildClient(String endpoint, String accessKey, String secretKey) {
		return MinioClient.builder()
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.build();
	}
}
