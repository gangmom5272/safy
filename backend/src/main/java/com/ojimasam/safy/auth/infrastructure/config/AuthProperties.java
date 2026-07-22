package com.ojimasam.safy.auth.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		String jwtSecret,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		String refreshCookieName,
		boolean cookieSecure,
		String cookieSameSite,
		String oauth2SuccessRedirectUri,
		String oauth2FailureRedirectUri
) {
}
