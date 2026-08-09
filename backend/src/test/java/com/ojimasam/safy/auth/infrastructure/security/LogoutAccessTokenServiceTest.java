package com.ojimasam.safy.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

		Optional<Jwt> revoked = new LogoutAccessTokenService(jwtDecoder, blacklist)
				.revokeIfValid("Bearer valid-token");

		verify(blacklist).revoke(jwt);
		// 로그아웃 엔드포인트는 인증 없이도 호출되므로, 누가 나가는지는 이 반환값으로만 알 수 있다.
		assertThat(revoked).contains(jwt);
	}

	@Test
	void ignoresExpiredOrInvalidBearerSoCookieLogoutCanContinue() {
		JwtDecoder jwtDecoder = mock(JwtDecoder.class);
		AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);
		when(jwtDecoder.decode("expired-token")).thenThrow(new BadJwtException("expired"));

		Optional<Jwt> revoked = new LogoutAccessTokenService(jwtDecoder, blacklist)
				.revokeIfValid("Bearer expired-token");

		verifyNoInteractions(blacklist);
		assertThat(revoked).isEmpty();
	}

	@Test
	void returnsEmptyWhenNoBearerHeader() {
		JwtDecoder jwtDecoder = mock(JwtDecoder.class);
		AccessTokenBlacklist blacklist = mock(AccessTokenBlacklist.class);

		LogoutAccessTokenService service = new LogoutAccessTokenService(jwtDecoder, blacklist);

		assertThat(service.revokeIfValid(null)).isEmpty();
		assertThat(service.revokeIfValid("Bearer ")).isEmpty();
		verifyNoInteractions(blacklist);
	}

	private Jwt jwt() {
		Instant now = Instant.now();
		return new Jwt("valid-token", now, now.plusSeconds(900), Map.of("alg", "HS256"),
				Map.of("sub", "15", "jti", "access-id"));
	}
}
