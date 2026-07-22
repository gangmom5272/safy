package com.ojimasam.safy.domain.user.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.domain.auth.security.RefreshTokenStore;
import com.ojimasam.safy.domain.user.entity.User;
import com.ojimasam.safy.domain.user.entity.UserRole;
import com.ojimasam.safy.domain.user.entity.UserStatus;
import com.ojimasam.safy.domain.user.repository.UserRepository;

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
