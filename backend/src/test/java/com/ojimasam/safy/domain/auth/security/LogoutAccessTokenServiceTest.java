package com.ojimasam.safy.domain.auth.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class LogoutAccessTokenServiceTest {
	@Test
	void blacklistsValidBearerToken() {
		JwtDecoder jwtDecoder = mock(JwtDecoder.class);
		AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
		Jwt jwt = jwt();
		when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

		new LogoutAccessTokenService(jwtDecoder, blacklist).revokeIfValid("Bearer valid-token");

		verify(blacklist).revoke(jwt);
	}

	@Test
	void ignoresExpiredOrInvalidBearerSoCookieLogoutCanContinue() {
		JwtDecoder jwtDecoder = mock(JwtDecoder.class);
		AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
		when(jwtDecoder.decode("expired-token")).thenThrow(new BadJwtException("expired"));

		new LogoutAccessTokenService(jwtDecoder, blacklist).revokeIfValid("Bearer expired-token");

		verifyNoInteractions(blacklist);
	}

	private Jwt jwt() {
		Instant now = Instant.now();
		return new Jwt("valid-token", now, now.plusSeconds(900), Map.of("alg", "HS256"),
				Map.of("sub", "15", "jti", "access-id"));
	}
}
