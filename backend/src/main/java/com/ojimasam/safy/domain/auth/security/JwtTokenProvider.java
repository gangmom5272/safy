package com.ojimasam.safy.domain.auth.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.domain.auth.config.AuthProperties;
import com.ojimasam.safy.domain.user.entity.User;

@Component
public class JwtTokenProvider {
	private final JwtEncoder jwtEncoder;
	private final AuthProperties properties;

	public JwtTokenProvider(JwtEncoder jwtEncoder, AuthProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String createAccessToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("safy")
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.subject(user.getId().toString())
				.claim("role", user.getRole().name())
				.claim("tokenVersion", user.getTokenVersion())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTokenExpiresInSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}
}
