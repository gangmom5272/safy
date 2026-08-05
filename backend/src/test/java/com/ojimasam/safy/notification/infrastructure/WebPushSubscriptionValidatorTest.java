package com.ojimasam.safy.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebPushSubscriptionValidatorTest {
	private final WebPushSubscriptionValidator validator = new WebPushSubscriptionValidator(new WebPushProperties(
			true, "mailto:test@example.com", "public", "private", 86400,
			Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(1),
			Duration.ofMinutes(1), Duration.ofMinutes(1), 5,
			List.of("fcm.googleapis.com", ".notify.windows.com")));

	@Test
	void acceptsKnownHttpsPushEndpointAndValidKeys() {
		assertDoesNotThrow(() -> validator.validateEndpoint("https://fcm.googleapis.com/fcm/send/token"));
		assertDoesNotThrow(() -> validator.validateKeys(encoded(65), encoded(16)));
	}

	@Test
	void rejectsUntrustedOrNonHttpsEndpoint() {
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateEndpoint("http://127.0.0.1/internal"));
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateEndpoint("https://attacker.example/push"));
	}

	@Test
	void acceptsConfiguredHostSuffixOnlyAtLabelBoundary() {
		assertDoesNotThrow(() -> validator.validateEndpoint("https://wns2.notify.windows.com/w/token"));
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateEndpoint("https://evilnotify.windows.com/w/token"));
	}

	private String encoded(int size) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[size]);
	}
}
