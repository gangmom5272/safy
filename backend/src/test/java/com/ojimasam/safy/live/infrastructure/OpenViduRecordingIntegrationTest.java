package com.ojimasam.safy.live.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.ojimasam.safy.live.application.LiveRecordingProperties;
import com.ojimasam.safy.live.application.OpenViduProperties;
import com.ojimasam.safy.live.application.dto.LiveRecordingRequest;
import com.ojimasam.safy.media.application.StoredMediaObject;
import com.ojimasam.safy.media.infrastructure.MinioMediaObjectStorage;

import io.minio.MinioClient;

@EnabledIfEnvironmentVariable(named = "OPENVIDU_RECORDING_INTEGRATION_TEST", matches = "true")
class OpenViduRecordingIntegrationTest {
	@Test
	void participantEgressStoresMp4AndPeriodicJpegs() throws Exception {
		String openViduUrl = environment("OPENVIDU_URL", "http://localhost:7880");
		String apiKey = environment("OPENVIDU_API_KEY", "devkey");
		String apiSecret = environment("OPENVIDU_API_SECRET", "secret");
		String roomName = required("OPENVIDU_MANUAL_ROOM_NAME");
		String participantIdentity = environment("OPENVIDU_MANUAL_PARTICIPANT", "manual-reporter");
		String bucket = environment("MINIO_UPLOAD_BUCKET", "safy");
		String minioEndpoint = environment("MINIO_ENDPOINT", "http://localhost:9202");
		String minioAccessKey = required("MINIO_ACCESS_KEY");
		String minioSecretKey = required("MINIO_SECRET_KEY");
		String egressS3Endpoint = environment(
				"LIVE_RECORDING_S3_ENDPOINT", "http://host.docker.internal:9202");

		LiveKitMediaGateway gateway = new LiveKitMediaGateway(
				new OpenViduProperties(openViduUrl, apiKey, apiSecret, null, null, null, null),
				new LiveRecordingProperties(true, bucket, egressS3Endpoint, minioAccessKey,
						minioSecretKey, null, Duration.ofSeconds(2), 640, 360, 4, Duration.ofSeconds(2)));
		MinioClient minioClient = MinioClient.builder()
				.endpoint(minioEndpoint)
				.credentials(minioAccessKey, minioSecretKey)
				.build();
		MinioMediaObjectStorage storage = new MinioMediaObjectStorage(minioClient, minioClient, minioEndpoint);

		String token = UUID.randomUUID().toString();
		String recordingKey = "integration-test/recordings/" + token + ".mp4";
		String framePrefix = "integration-test/frames/" + token + "/";
		String egressId = gateway.startRecording(new LiveRecordingRequest(
				roomName, participantIdentity, recordingKey, framePrefix));

		try {
			Thread.sleep(Duration.ofSeconds(8));
			gateway.stopRecording(egressId);

			List<StoredMediaObject> frames = awaitFrames(storage, bucket, framePrefix);
			awaitObject(storage, bucket, recordingKey);

			assertThat(frames).isNotEmpty();
			assertThat(frames).allMatch(frame -> frame.size() > 0);
			assertThat(storage.stat(bucket, recordingKey).size()).isPositive();
		} finally {
			storage.list(bucket, framePrefix, null)
					.forEach(frame -> storage.delete(bucket, frame.objectKey()));
			try {
				storage.delete(bucket, recordingKey);
			} catch (IllegalStateException ignored) {
				// Egress 시작 자체가 실패한 경우 정리할 녹화 객체가 없다.
			}
		}
	}

	private List<StoredMediaObject> awaitFrames(
			MinioMediaObjectStorage storage, String bucket, String prefix) throws InterruptedException {
		for (int attempt = 0; attempt < 20; attempt++) {
			List<StoredMediaObject> frames = storage.list(bucket, prefix, null);
			if (!frames.isEmpty()) return frames;
			Thread.sleep(500L);
		}
		return List.of();
	}

	private void awaitObject(MinioMediaObjectStorage storage, String bucket, String objectKey)
			throws InterruptedException {
		for (int attempt = 0; attempt < 20; attempt++) {
			try {
				storage.stat(bucket, objectKey);
				return;
			} catch (IllegalStateException exception) {
				Thread.sleep(500L);
			}
		}
		throw new AssertionError("녹화 객체가 제한 시간 안에 생성되지 않았습니다: " + objectKey);
	}

	private String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
		return value;
	}

	private String environment(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
