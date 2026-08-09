package com.ojimasam.safy.live.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.live.application.LiveWebhookEvent;
import com.ojimasam.safy.live.application.OpenViduProperties;

class LiveKitWebhookParserTest {
	@Test
	void parsesSignedParticipantJoinedWebhook() throws Exception {
		String body = """
				{"event":"participant_joined","room":{"name":"report-session"},"participant":{"identity":"reporter-1"}}
				""".trim();
		String checksum = Base64.getEncoder().encodeToString(
				MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
		String authorization = sign(checksum);
		LiveKitWebhookParser parser = new LiveKitWebhookParser(
				new OpenViduProperties("http://localhost:7880", "devkey", "secret", null, null, null, null));

		LiveWebhookEvent event = parser.parse(body, authorization);

		assertThat(event.type()).isEqualTo(LiveWebhookEvent.Type.PARTICIPANT_JOINED);
		assertThat(event.roomName()).isEqualTo("report-session");
		assertThat(event.participantIdentity()).isEqualTo("reporter-1");
	}

	private String sign(String checksum) throws Exception {
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
				.getBytes(StandardCharsets.UTF_8));
		String payload = encoder.encodeToString(("{\"iss\":\"devkey\",\"sha256\":\"" + checksum + "\"}")
				.getBytes(StandardCharsets.UTF_8));
		String unsigned = header + "." + payload;
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
	}
}
