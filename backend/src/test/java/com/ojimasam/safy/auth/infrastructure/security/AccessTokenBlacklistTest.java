package com.ojimasam.safy.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTokenBlacklistTest {
	@Test
	@SuppressWarnings("unchecked")
	void revokesAccessTokenOnlyForItsRemainingLifetime() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		AccessTokenBlacklist blacklist = new AccessTokenBlacklist(redisTemplate);
		Instant now = Instant.now();
		Jwt jwt = new Jwt("token", now, now.plusSeconds(900), Map.of("alg", "HS256"),
				Map.of("sub", "15", "jti", "access-id"));

		blacklist.revoke(jwt);

		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(valueOperations).set(eq("auth:access:blacklist:access-id"), eq("revoked"), ttl.capture());
		assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void reportsRevokedToken() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(redisTemplate.hasKey("auth:access:blacklist:access-id")).thenReturn(true);

		assertThat(new AccessTokenBlacklist(redisTemplate).isRevoked("access-id")).isTrue();
	}
}
