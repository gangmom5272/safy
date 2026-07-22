package com.ojimasam.safy.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.auth.infrastructure.config.AuthProperties;

@Component
public class RefreshTokenStore {
	private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
	private static final String USER_KEY_PREFIX = "auth:refresh:user:";
	private final SecureRandom secureRandom = new SecureRandom();
	private final StringRedisTemplate redisTemplate;
	private final AuthProperties properties;

	public RefreshTokenStore(StringRedisTemplate redisTemplate, AuthProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	public String issue(Long userId, long tokenVersion) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String tokenHash = hash(token);
		redisTemplate.opsForValue().set(tokenKey(tokenHash), value(userId, tokenVersion), properties.refreshTokenTtl());
		redisTemplate.opsForSet().add(userKey(userId), tokenHash);
		redisTemplate.expire(userKey(userId), properties.refreshTokenTtl());
		return token;
	}

	public Optional<RefreshTokenIdentity> consume(String token) {
		String tokenHash = hash(token);
		String value = redisTemplate.opsForValue().getAndDelete(tokenKey(tokenHash));
		if (value == null) return Optional.empty();

		Optional<RefreshTokenIdentity> identity = parse(value);
		identity.ifPresent(found -> redisTemplate.opsForSet().remove(userKey(found.userId()), tokenHash));
		return identity;
	}

	public void revoke(String token) {
		if (token == null || token.isBlank()) return;

		String tokenHash = hash(token);
		String value = redisTemplate.opsForValue().getAndDelete(tokenKey(tokenHash));
		if (value != null) parseUserId(value)
				.ifPresent(userId -> redisTemplate.opsForSet().remove(userKey(userId), tokenHash));
	}

	public void revokeAll(Long userId) {
		String userKey = userKey(userId);
		Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
		if (tokenHashes != null && !tokenHashes.isEmpty()) {
			List<String> tokenKeys = tokenHashes.stream().map(this::tokenKey).toList();
			redisTemplate.delete(tokenKeys);
		}
		redisTemplate.delete(userKey);
	}

	private String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String tokenKey(String tokenHash) {
		return TOKEN_KEY_PREFIX + tokenHash;
	}

	private String userKey(Long userId) {
		return USER_KEY_PREFIX + userId;
	}

	private String value(Long userId, long tokenVersion) {
		return userId + ":" + tokenVersion;
	}

	private Optional<RefreshTokenIdentity> parse(String value) {
		int separator = value.indexOf(':');
		if (separator <= 0 || separator == value.length() - 1) return Optional.empty();
		try {
			Long userId = Long.parseLong(value.substring(0, separator));
			long tokenVersion = Long.parseLong(value.substring(separator + 1));
			return Optional.of(new RefreshTokenIdentity(userId, tokenVersion));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private Optional<Long> parseUserId(String value) {
		int separator = value.indexOf(':');
		String userId = separator < 0 ? value : value.substring(0, separator);
		try {
			return Optional.of(Long.parseLong(userId));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	public record RefreshTokenIdentity(Long userId, long tokenVersion) {
	}
}
