package com.ojimasam.safy.admin.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.ControlCenterRepository;
import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.auth.domain.AdminCredentialRepository;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRole;
import com.ojimasam.safy.user.domain.UserStatus;
import com.ojimasam.safy.user.domain.UserRepository;

class AdminServiceTest {
	@Test
	void passwordChangeInvalidatesEveryToken() {
		UserRepository userRepository = mock(UserRepository.class);
		AdminCredentialRepository credentialRepository = mock(AdminCredentialRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		AdminCredential credential = mock(AdminCredential.class);
		User user = mock(User.class);
		when(credentialRepository.findById(15L)).thenReturn(Optional.of(credential));
		when(credential.getPasswordHash()).thenReturn("old-hash");
		when(credential.getUser()).thenReturn(user);
		when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
		when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
		AdminService service = new AdminService(userRepository, credentialRepository,
				mock(AdminCenterMembershipRepository.class), mock(ControlCenterRepository.class),
				passwordEncoder, refreshTokenStore);

		service.changePassword(15L, "old-password", "new-password");

		verify(credential).changePassword("new-hash");
		verify(user).invalidateTokens();
		verify(refreshTokenStore).revokeAll(15L);
	}

	@Test
	void suspensionInvalidatesAccessAndAllRefreshTokens() {
		UserRepository userRepository = mock(UserRepository.class);
		AdminCredentialRepository credentialRepository = mock(AdminCredentialRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(15L);
		when(user.getRole()).thenReturn(UserRole.OPERATOR);
		when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);
		when(credentialRepository.findById(15L)).thenReturn(Optional.of(mock(AdminCredential.class)));
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(15L))
				.thenReturn(Optional.of(mock(AdminCenterMembership.class)));
		AdminService service = new AdminService(userRepository, credentialRepository, membershipRepository,
				mock(ControlCenterRepository.class), mock(PasswordEncoder.class), refreshTokenStore);

		service.changeStatus(15L, UserStatus.SUSPENDED);

		verify(user).changeStatus(UserStatus.SUSPENDED);
		verify(user).invalidateTokens();
		verify(refreshTokenStore).revokeAll(15L);
	}
}
