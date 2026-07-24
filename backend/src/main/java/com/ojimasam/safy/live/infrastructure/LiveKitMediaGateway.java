package com.ojimasam.safy.live.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ojimasam.safy.live.application.LiveMediaException;
import com.ojimasam.safy.live.application.LiveMediaGateway;
import com.ojimasam.safy.live.application.LiveParticipantRole;
import com.ojimasam.safy.live.application.OpenViduProperties;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import retrofit2.Response;

@Component
public class LiveKitMediaGateway implements LiveMediaGateway {
	private final OpenViduProperties properties;

	public LiveKitMediaGateway(OpenViduProperties properties) {
		this.properties = properties;
	}

	@Override
	public void createRoom(String roomName) {
		ensureConfigured();
		try {
			Response<?> response = client().createRoom(roomName).execute();
			if (!response.isSuccessful()) {
				throw new LiveMediaException("OpenVidu room creation failed: HTTP " + response.code());
			}
		} catch (IOException exception) {
			throw new LiveMediaException("OpenVidu room creation failed", exception);
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
