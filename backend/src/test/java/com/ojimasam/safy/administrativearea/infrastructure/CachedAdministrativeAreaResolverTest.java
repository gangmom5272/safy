package com.ojimasam.safy.administrativearea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

class CachedAdministrativeAreaResolverTest {
	private static final BigDecimal LATITUDE = new BigDecimal("37.5017945");
	private static final BigDecimal LONGITUDE = new BigDecimal("127.0400000");
	private static final String KEY = "location:area:37.5017945:127.0400000";

	private final KakaoAdministrativeAreaResolver delegate = mock(KakaoAdministrativeAreaResolver.class);
	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
	private CachedAdministrativeAreaResolver resolver;

	@BeforeEach
	void setUp() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		resolver = new CachedAdministrativeAreaResolver(delegate, redisTemplate);
	}

	@Test
	void cachesResolvedArea() {
		when(valueOperations.get(KEY)).thenReturn(null);
		when(delegate.resolve(LATITUDE, LONGITUDE))
				.thenReturn(Optional.of(new ResolvedAdministrativeArea("1168000000", "강남구")));

		var result = resolver.resolve(LATITUDE, LONGITUDE);

		assertThat(result).contains(new ResolvedAdministrativeArea("1168000000", "강남구"));
		verify(valueOperations).set(eq(KEY), eq("1168000000:강남구"), any(Duration.class));
	}

	@Test
	void readsFromCacheWithoutCallingKakao() {
		when(valueOperations.get(KEY)).thenReturn("1168000000:강남구");

		var result = resolver.resolve(LATITUDE, LONGITUDE);

		assertThat(result).contains(new ResolvedAdministrativeArea("1168000000", "강남구"));
		verify(delegate, never()).resolve(any(), any());
	}

	/** 저장 전 좌표(위치 조회)와 저장 후 좌표(numeric(10,7))가 같은 키로 떨어져야 한다. */
	@Test
	void normalizesCoordinateToColumnPrecision() {
		when(valueOperations.get(KEY)).thenReturn("1168000000:강남구");

		var result = resolver.resolve(new BigDecimal("37.50179451234567"), new BigDecimal("127.04"));

		assertThat(result).contains(new ResolvedAdministrativeArea("1168000000", "강남구"));
	}

	@Test
	void doesNotCacheCoordinateOutsideSeoul() {
		when(valueOperations.get(KEY)).thenReturn(null);
		when(delegate.resolve(LATITUDE, LONGITUDE)).thenReturn(Optional.empty());

		assertThat(resolver.resolve(LATITUDE, LONGITUDE)).isEmpty();
		verify(valueOperations, never()).set(any(), any(), any(Duration.class));
	}
}
