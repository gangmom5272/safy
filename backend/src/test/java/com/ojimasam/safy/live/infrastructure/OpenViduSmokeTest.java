package com.ojimasam.safy.live.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.nimbusds.jwt.SignedJWT;
import com.ojimasam.safy.live.application.LiveParticipantRole;
import com.ojimasam.safy.live.application.OpenViduProperties;

@EnabledIfEnvironmentVariable(named = "OPENVIDU_URL", matches = ".+")
class OpenViduSmokeTest {
	@Test
	void createsRoomIssuesRestrictedTokensAndClosesRoom() throws Exception {
		String url = System.getenv("OPENVIDU_URL");
		String apiKey = System.getenv("OPENVIDU_API_KEY");
		String apiSecret = System.getenv("OPENVIDU_API_SECRET");
		assumeTrue(isPresent(apiKey) && isPresent(apiSecret),
				"OPENVIDU_API_KEY and OPENVIDU_API_SECRET must be configured");

		OpenViduProperties properties = new OpenViduProperties(
				url, apiKey, apiSecret, Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(1));
		LiveKitMediaGateway gateway = new LiveKitMediaGateway(properties);
		String roomName = "safy-smoke-" + UUID.randomUUID();

		gateway.createRoom(roomName);
		try {
			String reporterToken = gateway.issueToken(
					roomName, "smoke-reporter", LiveParticipantRole.REPORTER, Duration.ofMinutes(2));
			String viewerToken = gateway.issueToken(
					roomName, "smoke-viewer", LiveParticipantRole.VIEWER, Duration.ofMinutes(2));

			assertThat(SignedJWT.parse(reporterToken).getJWTClaimsSet().getSubject())
					.isEqualTo("smoke-reporter");
			assertThat(SignedJWT.parse(viewerToken).getJWTClaimsSet().getSubject())
					.isEqualTo("smoke-viewer");
		} finally {
			gateway.closeRoom(roomName);
		}
	}

	private boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
