package com.ojimasam.safy.user.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.user.domain.UserRepository;

class UserServiceTest {
	@Test
	void withdrawalInvalidatesAccessAndAllRefreshTokens() {
		UserRepository userRepository = mock(UserRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getRole()).thenReturn(UserRole.CITIZEN);
		UserService service = new UserService(userRepository, refreshTokenStore);

		service.withdraw(15L);

		verify(user).changeStatus(UserStatus.WITHDRAWN);
		verify(user).invalidateTokens();
		verify(refreshTokenStore).revokeAll(15L);
	}
}
