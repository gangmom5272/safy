package com.ojimasam.safy.live.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.SignedJWT;
import com.ojimasam.safy.live.application.LiveParticipantRole;
import com.ojimasam.safy.live.application.OpenViduProperties;

class LiveKitMediaGatewayTest {
	private LiveKitMediaGateway gateway;

	@BeforeEach
	void setUp() {
		gateway = new LiveKitMediaGateway(
				new OpenViduProperties(
						"ws://localhost:7880", "devkey",
						"0123456789012345678901234567890123456789012345678901234567890123",
						Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofMinutes(1),
						Duration.ofSeconds(60)),
				new com.ojimasam.safy.live.application.LiveRecordingProperties(
						false, "safy", "http://localhost:9000", "key", "secret", null, null, 0, 0));
	}

	@Test
	void reporterCanOnlyPublishCameraAndCannotSubscribe() throws Exception {
		Map<String, Object> grants = grants(gateway.issueToken(
				"report-session", "reporter-1", LiveParticipantRole.REPORTER, Duration.ofMinutes(5)));

		assertThat(grants).containsEntry("roomJoin", true)
				.containsEntry("room", "report-session")
				.containsEntry("canSubscribe", false)
				.containsEntry("canPublishData", false);
		assertThat(grants.get("canPublishSources")).isEqualTo(List.of("camera"));
	}

	@Test
	void viewerCannotPublishAndCanSubscribe() throws Exception {
		Map<String, Object> grants = grants(gateway.issueToken(
				"report-session", "operator-2", LiveParticipantRole.VIEWER, Duration.ofMinutes(5)));

		assertThat(grants).containsEntry("canPublish", false)
				.containsEntry("canSubscribe", true)
				.containsEntry("canPublishData", false);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> grants(String token) throws Exception {
		return (Map<String, Object>) SignedJWT.parse(token).getJWTClaimsSet().getClaim("video");
	}
}
