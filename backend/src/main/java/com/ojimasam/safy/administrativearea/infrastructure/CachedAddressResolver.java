package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.administrativearea.application.AddressResolver;

/**
 * 같은 좌표를 위치 조회와 제보 라우팅이 각각 조회하므로 해석기 앞단에 캐시를 둔다.
 * 라우팅 재시도도 같은 좌표를 다시 조회한다.
 */
@Component
@Primary
public class CachedAddressResolver implements AddressResolver {
	private static final String KEY_PREFIX = "location:address:";
	private static final Duration TTL = Duration.ofDays(30);

	private final AddressResolver delegate;
	private final StringRedisTemplate redisTemplate;

	public CachedAddressResolver(KakaoAddressResolver delegate, StringRedisTemplate redisTemplate) {
		this.delegate = delegate;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Optional<String> resolve(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");

		String key = CoordinateCacheKey.of(KEY_PREFIX, latitude, longitude);
		String cached = redisTemplate.opsForValue().get(key);
		if (cached != null) return Optional.of(cached);

		Optional<String> resolved = delegate.resolve(latitude, longitude);
		// 주소가 없는 좌표(바다 등)는 캐싱하지 않는다. 재조회 비용보다 빈 값을 오래 붙드는 편이 나쁘다.
		resolved.ifPresent(address -> redisTemplate.opsForValue().set(key, address, TTL));
		return resolved;
	}
}
