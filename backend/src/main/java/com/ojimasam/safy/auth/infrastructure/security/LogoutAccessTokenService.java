package com.ojimasam.safy.auth.infrastructure.security;

import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
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

	/**
	 * Access Token 을 폐기하고 그 토큰의 주인을 돌려준다. 로그아웃 엔드포인트는 인증 없이도
	 * 호출되므로 누가 나가는지 알 수 있는 곳이 여기뿐이다(근무 상태 오프라인 전환에 쓴다).
	 *
	 * @return 유효한 토큰이었으면 해독 결과, 없거나 잘못된 토큰이면 비어 있음
	 */
	public Optional<Jwt> revokeIfValid(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0,
				BEARER_PREFIX, 0, BEARER_PREFIX.length())) return Optional.empty();

		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) return Optional.empty();
		try {
			Jwt jwt = jwtDecoder.decode(token);
			accessTokenBlacklist.revoke(jwt);
			return Optional.of(jwt);
		} catch (JwtException ignored) {
			// 만료되었거나 잘못된 Access Token이어도 Refresh Token과 쿠키 로그아웃은 계속한다.
			return Optional.empty();
		}
	}
}
