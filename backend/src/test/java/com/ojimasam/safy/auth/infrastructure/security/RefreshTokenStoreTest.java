package com.ojimasam.safy.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ojimasam.safy.auth.infrastructure.config.AuthProperties;

class RefreshTokenStoreTest {
	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOperations;
	private SetOperations<String, String> setOperations;
	private RefreshTokenStore store;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		setOperations = mock(SetOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		AuthProperties properties = new AuthProperties("secret", Duration.ofMinutes(15), Duration.ofDays(14),
				"refresh", false, "Lax", "success", "failure");
		store = new RefreshTokenStore(redisTemplate, properties);
	}

	@Test
	void issuesTokenAndIndexesItByUser() {
		String token = store.issue(15L, 3L);

		assertThat(token).isNotBlank();
		ArgumentCaptor<String> tokenKey = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(tokenKey.capture(), eq("15:3"), eq(Duration.ofDays(14)));
		assertThat(tokenKey.getValue()).startsWith("auth:refresh:");
		verify(setOperations).add(eq("auth:refresh:user:15"), anyString());
		verify(redisTemplate).expire("auth:refresh:user:15", Duration.ofDays(14));
	}

	@Test
	void consumesTokenOnlyOnceAndRemovesUserIndex() {
		when(valueOperations.getAndDelete(anyString())).thenReturn("15:3");

		assertThat(store.consume("refresh-token")).contains(
				new RefreshTokenStore.RefreshTokenIdentity(15L, 3L));
		verify(setOperations).remove(eq("auth:refresh:user:15"), anyString());
	}

	@Test
	void rejectsLegacyTokenWithoutVersion() {
		when(valueOperations.getAndDelete(anyString())).thenReturn("15");

		assertThat(store.consume("legacy-refresh-token")).isEmpty();
	}

	@Test
	void revokesEveryTokenIndexedForUser() {
		when(setOperations.members("auth:refresh:user:15")).thenReturn(Set.of("hash-a", "hash-b"));

		store.revokeAll(15L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
		verify(redisTemplate).delete(keys.capture());
		assertThat(keys.getValue()).containsExactlyInAnyOrder("auth:refresh:hash-a", "auth:refresh:hash-b");
		verify(redisTemplate).delete("auth:refresh:user:15");
	}
}
