package com.ojimasam.safy.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.ojimasam.safy.media.application.StoredMediaObject;

import io.minio.MinioClient;

@EnabledIfEnvironmentVariable(named = "MINIO_INTEGRATION_TEST", matches = "true")
class MinioMediaObjectStorageIntegrationTest {
	@Test
	void putsReadsListsAndDeletesObject() {
		String endpoint = environment("MINIO_ENDPOINT", "http://localhost:9202");
		String accessKey = environment("MINIO_ACCESS_KEY", "safy");
		String secretKey = environment("MINIO_SECRET_KEY", "safy-local-password");
		String bucket = environment("MINIO_UPLOAD_BUCKET", "safy");
		MinioClient client = MinioClient.builder()
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.build();
		MinioMediaObjectStorage storage = new MinioMediaObjectStorage(client, client, endpoint);

		String prefix = "integration-test/" + UUID.randomUUID() + "/";
		String objectKey = prefix + "frame.jpg";
		byte[] content = "test-image-bytes".getBytes(StandardCharsets.UTF_8);

		try {
			storage.put(bucket, objectKey, content, "image/jpeg");

			assertThat(storage.read(bucket, objectKey)).isEqualTo(content);
			assertThat(storage.readPrefix(bucket, objectKey, 4))
					.isEqualTo("test".getBytes(StandardCharsets.UTF_8));
			assertThat(storage.stat(bucket, objectKey).size()).isEqualTo(content.length);
			assertThat(storage.list(bucket, prefix, null))
					.extracting(StoredMediaObject::objectKey)
					.containsExactly(objectKey);
			assertThat(storage.list(bucket, prefix, objectKey)).isEmpty();
		} finally {
			storage.delete(bucket, objectKey);
		}
	}

	private String environment(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
