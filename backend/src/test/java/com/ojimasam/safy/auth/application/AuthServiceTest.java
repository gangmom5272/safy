package com.ojimasam.safy.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.auth.domain.AdminCredentialRepository;
import com.ojimasam.safy.auth.domain.SocialAccountRepository;
import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.auth.infrastructure.security.JwtTokenProvider;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore.RefreshTokenIdentity;
import com.ojimasam.safy.auth.infrastructure.security.SocialLoginCodeStore;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.user.domain.UserRepository;

class AuthServiceTest {
	@Test
	void rejectsTemporarilyLockedAdminBeforePasswordVerification() {
		AdminCredentialRepository credentialRepository = mock(AdminCredentialRepository.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		AdminCredential credential = mock(AdminCredential.class);
		when(credentialRepository.findByLoginId("operator01")).thenReturn(Optional.of(credential));
		when(credential.isLoginLocked(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		AuthService service = new AuthService(mock(UserRepository.class), mock(SocialAccountRepository.class),
				credentialRepository, passwordEncoder, mock(JwtTokenProvider.class),
				mock(RefreshTokenStore.class), mock(SocialLoginCodeStore.class));

		assertThatThrownBy(() -> service.adminLogin("operator01", "password"))
				.isInstanceOf(BusinessException.class);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	void rejectsRefreshTokenIssuedWithOldTokenVersion() {
		UserRepository userRepository = mock(UserRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
		User user = mock(User.class);
		when(refreshTokenStore.consume("old-refresh-token"))
				.thenReturn(Optional.of(new RefreshTokenIdentity(15L, 3L)));
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getTokenVersion()).thenReturn(4L);
		AuthService service = new AuthService(userRepository, mock(SocialAccountRepository.class),
				mock(AdminCredentialRepository.class), mock(PasswordEncoder.class), jwtTokenProvider,
				refreshTokenStore, mock(SocialLoginCodeStore.class));

		assertThatThrownBy(() -> service.refresh("old-refresh-token"))
				.isInstanceOf(BusinessException.class);
		verifyNoInteractions(jwtTokenProvider);
	}
}
