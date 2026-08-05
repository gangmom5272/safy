package com.ojimasam.safy.notification.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class WebPushSubscriptionValidator {
	private final WebPushProperties properties;

	public WebPushSubscriptionValidator(WebPushProperties properties) {
		this.properties = properties;
	}

	public String validateEndpoint(String endpoint) {
		try {
			URI uri = new URI(endpoint.trim()).normalize();
			String host = uri.getHost();
			if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
					|| uri.getFragment() != null
					|| (uri.getPort() != -1 && uri.getPort() != 443) || !allowed(host)) {
				throw new IllegalArgumentException("허용되지 않은 Web Push endpoint입니다.");
			}
			return uri.toASCIIString();
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Web Push endpoint 형식이 올바르지 않습니다.", exception);
		}
	}

	public void validateKeys(String p256dh, String auth) {
		try {
			byte[] publicKey = Base64.getUrlDecoder().decode(pad(p256dh.trim()));
			byte[] authSecret = Base64.getUrlDecoder().decode(pad(auth.trim()));
			if (publicKey.length != 65 || authSecret.length != 16) {
				throw new IllegalArgumentException("Web Push 구독 키 길이가 올바르지 않습니다.");
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Web Push 구독 키 형식이 올바르지 않습니다.", exception);
		}
	}

	private boolean allowed(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		return properties.allowedEndpointHosts().stream().map(value -> value.toLowerCase(Locale.ROOT).trim())
				.anyMatch(value -> !value.isEmpty() && (normalized.equals(value)
						|| (value.startsWith(".") && normalized.endsWith(value))));
	}

	private String pad(String value) {
		return value + "=".repeat((4 - value.length() % 4) % 4);
	}
}
