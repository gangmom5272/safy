package com.ojimasam.safy.auth.infrastructure.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenBlacklist {
	private static final String KEY_PREFIX = "auth:access:blacklist:";

	private final StringRedisTemplate redisTemplate;

	public AccessTokenBlacklist(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void revoke(Jwt jwt) {
		if (jwt == null || jwt.getId() == null || jwt.getExpiresAt() == null) return;

		Duration remaining = Duration.between(Instant.now(), jwt.getExpiresAt());
		if (remaining.isNegative() || remaining.isZero()) return;

		redisTemplate.opsForValue().set(key(jwt.getId()), "revoked", remaining);
	}

	public boolean isRevoked(String jti) {
		return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
	}

	private String key(String jti) {
		return KEY_PREFIX + jti;
	}
}
