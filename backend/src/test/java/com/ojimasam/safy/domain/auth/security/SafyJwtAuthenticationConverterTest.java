package com.ojimasam.safy.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.ojimasam.safy.domain.user.entity.User;
import com.ojimasam.safy.domain.user.entity.UserRole;
import com.ojimasam.safy.domain.user.repository.UserRepository;

class SafyJwtAuthenticationConverterTest {
	private UserRepository userRepository;
	private AccessTokenBlacklist accessTokenBlacklist;
	private SafyJwtAuthenticationConverter converter;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		accessTokenBlacklist = mock(AccessTokenBlacklist.class);
		converter = new SafyJwtAuthenticationConverter(userRepository, accessTokenBlacklist);
	}

	@Test
	void authenticatesWithCurrentDatabaseRole() {
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(true);
		when(user.getTokenVersion()).thenReturn(3L);
		when(user.getRole()).thenReturn(UserRole.OPERATOR);

		var authentication = converter.convert(jwt(15L, 3L));

		assertThat(authentication.getName()).isEqualTo("15");
		assertThat(authentication.getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_OPERATOR");
	}

	@Test
	void rejectsInactiveUser() {
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(false);

		assertThatThrownBy(() -> converter.convert(jwt(15L, 3L)))
				.isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsOldTokenVersion() {
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(true);
		when(user.getTokenVersion()).thenReturn(4L);

		assertThatThrownBy(() -> converter.convert(jwt(15L, 3L)))
				.isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void rejectsBlacklistedAccessTokenBeforeDatabaseLookup() {
		when(accessTokenBlacklist.isRevoked("access-id")).thenReturn(true);

		assertThatThrownBy(() -> converter.convert(jwt(15L, 3L)))
				.isInstanceOf(InvalidBearerTokenException.class);
		verifyNoInteractions(userRepository);
	}

	private Jwt jwt(Long userId, Long tokenVersion) {
		Instant now = Instant.now();
		return new Jwt("token", now, now.plusSeconds(900),
				Map.of("alg", "HS256"),
				Map.of("sub", userId.toString(), "jti", "access-id",
						"role", "CITIZEN", "tokenVersion", tokenVersion));
	}
}
