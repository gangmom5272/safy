package com.ojimasam.safy.auth.infrastructure.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class LogoutAccessTokenService {
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;
	private final AccessTokenBlacklist accessTokenBlacklist;

	public LogoutAccessTokenService(JwtDecoder jwtDecoder, AccessTokenBlacklist accessTokenBlacklist) {
		this.jwtDecoder = jwtDecoder;
		this.accessTokenBlacklist = accessTokenBlacklist;
	}

	public void revokeIfValid(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0,
				BEARER_PREFIX, 0, BEARER_PREFIX.length())) return;

		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) return;
		try {
			accessTokenBlacklist.revoke(jwtDecoder.decode(token));
		} catch (JwtException ignored) {
			// 만료되었거나 잘못된 Access Token이어도 Refresh Token과 쿠키 로그아웃은 계속한다.
		}
	}
}
