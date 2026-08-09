package com.ojimasam.safy.live.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ojimasam.safy.live.application.LiveMediaException;
import com.ojimasam.safy.live.application.LiveMediaGateway;
import com.ojimasam.safy.live.application.LiveParticipantRole;
import com.ojimasam.safy.live.application.LiveRecordingProperties;
import com.ojimasam.safy.live.application.dto.LiveRecordingRequest;
import com.ojimasam.safy.live.application.OpenViduProperties;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.EncodedOutputs;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitEgress;
import livekit.LivekitModels;
import retrofit2.Response;

@Component
public class LiveKitMediaGateway implements LiveMediaGateway {
	private final OpenViduProperties properties;
	private final LiveRecordingProperties recordingProperties;

	public LiveKitMediaGateway(OpenViduProperties properties, LiveRecordingProperties recordingProperties) {
		this.properties = properties;
		this.recordingProperties = recordingProperties;
	}

	@Override
	public void createRoom(String roomName) {
		ensureConfigured();
		RoomServiceClient client = client();
		try {
			Response<?> response = client.createRoom(roomName).execute();
			if (!response.isSuccessful()) {
				if (roomExists(client, roomName)) return;
				throw new LiveMediaException("OpenVidu room creation failed: HTTP " + response.code());
			}
		} catch (IOException exception) {
			if (roomExists(client, roomName)) return;
			throw new LiveMediaException("OpenVidu room creation failed", exception);
		}
	}

	private boolean roomExists(RoomServiceClient client, String roomName) {
		try {
			Response<List<LivekitModels.Room>> response = client.listRooms(List.of(roomName)).execute();
			return response.isSuccessful() && response.body() != null
					&& response.body().stream().anyMatch(room -> roomName.equals(room.getName()));
		} catch (IOException exception) {
			return false;
		}
	}

	@Override
	public String issueToken(String roomName, String identity, LiveParticipantRole role, Duration ttl) {
		ensureConfigured();
		try {
			AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
			token.setIdentity(identity);
			token.setName(identity);
			token.setTtl(ttl.toMillis());
			token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanPublishData(false));
			switch (role) {
				case REPORTER -> token.addGrants(new CanPublish(true), new CanSubscribe(false),
						new CanPublishSources(List.of("camera")));
				case VIEWER, AI -> token.addGrants(new CanPublish(false), new CanSubscribe(true));
			}
			return token.toJwt();
		} catch (RuntimeException exception) {
			throw new LiveMediaException("OpenVidu token creation failed", exception);
		}
	}

	@Override
	public void closeRoom(String roomName) {
		ensureConfigured();
		try {
			Response<?> response = client().deleteRoom(roomName).execute();
			if (!response.isSuccessful() && response.code() != 404) {
				throw new LiveMediaException("OpenVidu room deletion failed: HTTP " + response.code());
			}
		} catch (IOException exception) {
			throw new LiveMediaException("OpenVidu room deletion failed", exception);
		}
	}

	@Override
	public String serverUrl() {
		ensureConfigured();
		return properties.url();
	}

	@Override
	public String startRecording(LiveRecordingRequest request) {
		ensureConfigured();
		ensureRecordingConfigured();
		LivekitEgress.S3Upload s3 = s3Upload();
		// 한 번의 Egress 요청으로 원본 MP4 녹화와 주기적 프레임(JPEG) 추출을 동시에 수행한다.
		LivekitEgress.EncodedFileOutput file = LivekitEgress.EncodedFileOutput.newBuilder()
				.setFileType(LivekitEgress.EncodedFileType.MP4)
				.setFilepath(request.recordingObjectKey())
				.setS3(s3)
				.build();
		LivekitEgress.ImageOutput images = LivekitEgress.ImageOutput.newBuilder()
				.setCaptureInterval((int) recordingProperties.frameInterval().toSeconds())
				.setWidth(recordingProperties.frameWidth())
				.setHeight(recordingProperties.frameHeight())
				.setFilenamePrefix(request.framePrefix())
				.setFilenameSuffix(LivekitEgress.ImageFileSuffix.IMAGE_SUFFIX_TIMESTAMP)
				.setS3(s3)
				.build();
		try {
			Response<LivekitEgress.EgressInfo> response = egressClient()
					.startParticipantEgress(request.roomName(), request.participantIdentity(),
							new EncodedOutputs(file, null, null, images), false, null, null)
					.execute();
			if (!response.isSuccessful() || response.body() == null) {
				throw new LiveMediaException("Egress start failed: HTTP " + response.code());
			}
			return response.body().getEgressId();
		} catch (IOException exception) {
			throw new LiveMediaException("Egress start failed", exception);
		}
	}

	@Override
	public void stopRecording(String providerEgressId) {
		ensureConfigured();
		try {
			Response<LivekitEgress.EgressInfo> response = egressClient().stopEgress(providerEgressId).execute();
			// 이미 종료된 작업이면 미디어 서버가 404/400을 준다. 중단이 목적이므로 성공으로 본다.
			if (!response.isSuccessful() && response.code() != 404 && response.code() != 400) {
				throw new LiveMediaException("Egress stop failed: HTTP " + response.code());
			}
		} catch (IOException exception) {
			throw new LiveMediaException("Egress stop failed", exception);
		}
	}

	private LivekitEgress.S3Upload s3Upload() {
		return LivekitEgress.S3Upload.newBuilder()
				.setEndpoint(recordingProperties.s3Endpoint())
				.setBucket(recordingProperties.bucket())
				.setAccessKey(recordingProperties.s3AccessKey())
				.setSecret(recordingProperties.s3SecretKey())
				.setRegion(recordingProperties.region())
				// MinIO 등 자체 호스팅 S3 호환 스토리지는 virtual-host 방식이 아닌 path 방식을 써야 한다.
				.setForcePathStyle(true)
				.build();
	}

	private EgressServiceClient egressClient() {
		return EgressServiceClient.createClient(properties.url(), properties.apiKey(), properties.apiSecret());
	}

	private void ensureRecordingConfigured() {
		if (isBlank(recordingProperties.bucket()) || isBlank(recordingProperties.s3Endpoint())
				|| isBlank(recordingProperties.s3AccessKey()) || isBlank(recordingProperties.s3SecretKey())) {
			throw new LiveMediaException("Live recording storage configuration is missing");
		}
	}

	private RoomServiceClient client() {
		return RoomServiceClient.createClient(properties.url(), properties.apiKey(), properties.apiSecret());
	}

	private void ensureConfigured() {
		if (isBlank(properties.url()) || isBlank(properties.apiKey()) || isBlank(properties.apiSecret())) {
			throw new LiveMediaException("OpenVidu configuration is missing");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
