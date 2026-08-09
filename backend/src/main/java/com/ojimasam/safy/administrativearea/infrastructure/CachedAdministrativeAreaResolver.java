package com.ojimasam.safy.administrativearea.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

/**
 * 라우팅 재시도는 좌표 해석부터 전부 다시 돌린다. 관제센터 정원 초과로 재시도하는
 * 경우 좌표→자치구 결과는 달라질 수 없으므로 캐시로 반복 호출을 걷어낸다.
 * 카카오 호출이 실패하면 캐싱되지 않으므로 재시도는 정상적으로 다시 호출한다.
 */
@Component
@Primary
public class CachedAdministrativeAreaResolver implements AdministrativeAreaResolver {
	private static final String KEY_PREFIX = "location:area:";
	private static final Duration TTL = Duration.ofDays(30);

	private final AdministrativeAreaResolver delegate;
	private final StringRedisTemplate redisTemplate;

	public CachedAdministrativeAreaResolver(
			KakaoAdministrativeAreaResolver delegate,
			StringRedisTemplate redisTemplate
	) {
		this.delegate = delegate;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public Optional<ResolvedAdministrativeArea> resolve(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) throw new IllegalArgumentException("위도와 경도는 필수입니다.");

		String key = CoordinateCacheKey.of(KEY_PREFIX, latitude, longitude);
		Optional<ResolvedAdministrativeArea> cached = parse(redisTemplate.opsForValue().get(key));
		if (cached.isPresent()) return cached;

		Optional<ResolvedAdministrativeArea> resolved = delegate.resolve(latitude, longitude);
		// 서울 밖 좌표는 캐싱하지 않는다. 수동 라우팅으로 넘어가 재조회가 일어나지 않는다.
		resolved.ifPresent(area -> redisTemplate.opsForValue().set(key, format(area), TTL));
		return resolved;
	}

	private String format(ResolvedAdministrativeArea area) {
		return area.code() + ":" + area.name();
	}

	private Optional<ResolvedAdministrativeArea> parse(String value) {
		if (value == null) return Optional.empty();
		int separator = value.indexOf(':');
		if (separator <= 0 || separator == value.length() - 1) return Optional.empty();
		return Optional.of(new ResolvedAdministrativeArea(
				value.substring(0, separator),
				value.substring(separator + 1)
		));
	}
}
