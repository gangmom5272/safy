package com.ojimasam.safy.domain.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SocialLoginCodeStore {
	private static final String KEY_PREFIX = "auth:social-code:";
	private static final Duration TTL = Duration.ofSeconds(60);
	private final SecureRandom secureRandom = new SecureRandom();
	private final StringRedisTemplate redisTemplate;

	public SocialLoginCodeStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public String issue(Long userId, boolean newUser) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		redisTemplate.opsForValue().set(KEY_PREFIX + code, userId + ":" + newUser, TTL);
		return code;
	}

	public Optional<SocialLoginIdentity> consume(String code) {
		String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
		if (value == null) return Optional.empty();
		String[] parts = value.split(":", 2);
		return Optional.of(new SocialLoginIdentity(Long.valueOf(parts[0]), Boolean.parseBoolean(parts[1])));
	}

	public record SocialLoginIdentity(Long userId, boolean newUser) {}
}
