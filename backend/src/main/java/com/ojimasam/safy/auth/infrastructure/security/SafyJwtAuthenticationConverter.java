package com.ojimasam.safy.auth.infrastructure.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;

@Component
public class SafyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
	private final UserRepository userRepository;
	private final AccessTokenBlacklist accessTokenBlacklist;

	public SafyJwtAuthenticationConverter(UserRepository userRepository,
			AccessTokenBlacklist accessTokenBlacklist) {
		this.userRepository = userRepository;
		this.accessTokenBlacklist = accessTokenBlacklist;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		if (accessTokenBlacklist.isRevoked(jwt.getId())) {
			throw new InvalidBearerTokenException("폐기된 Access Token입니다.");
		}

		Long userId;
		try {
			userId = Long.valueOf(jwt.getSubject());
		} catch (RuntimeException exception) {
			throw new InvalidBearerTokenException("잘못된 사용자 식별자입니다.", exception);
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new InvalidBearerTokenException("존재하지 않는 사용자입니다."));
		if (!user.isActive()) {
			throw new InvalidBearerTokenException("비활성화된 계정입니다.");
		}

		Number tokenVersion = jwt.getClaim("tokenVersion");
		if (tokenVersion == null || tokenVersion.longValue() != user.getTokenVersion()) {
			throw new InvalidBearerTokenException("무효화된 Access Token입니다.");
		}

		return new JwtAuthenticationToken(jwt,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
				userId.toString());
	}
}
