package com.ojimasam.safy.auth.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		String jwtSecret,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		String refreshCookieName,
		/**
		 * 관리자 콘솔 전용 Refresh Token 쿠키 이름.
		 * 시민 앱과 관리자 콘솔은 같은 호스트(`/`, `/admin/`)에서 서비스되므로 쿠키 저장소를 공유한다.
		 * 이름이 하나면 나중에 로그인한 쪽이 상대 쿠키를 덮어써서 두 세션이 서로를 끊는다.
		 */
		String adminRefreshCookieName,
		boolean cookieSecure,
		String cookieSameSite,
		String oauth2SuccessRedirectUri,
		String oauth2FailureRedirectUri
) {
}
