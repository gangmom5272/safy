package com.ojimasam.safy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;

class UserServiceTest {
	@Test
	void meIncludesOperatorsAssignedControlCenter() {
		UserRepository userRepository = mock(UserRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		User user = mock(User.class);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		ControlCenter controlCenter = mock(ControlCenter.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(15L);
		when(user.getRole()).thenReturn(UserRole.OPERATOR);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(15L)).thenReturn(Optional.of(membership));
		when(membership.getControlCenterId()).thenReturn(23L);
		when(membership.getControlCenter()).thenReturn(controlCenter);
		when(controlCenter.getName()).thenReturn("강남구 재난안전상황실");
		UserService service = new UserService(userRepository, refreshTokenStore, membershipRepository);

		var response = service.me(15L);

		assertThat(response.controlCenterId()).isEqualTo(23L);
		assertThat(response.controlCenterName()).isEqualTo("강남구 재난안전상황실");
	}

	@Test
	void withdrawalInvalidatesAccessAndAllRefreshTokens() {
		UserRepository userRepository = mock(UserRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getRole()).thenReturn(UserRole.CITIZEN);
		UserService service = new UserService(
				userRepository,
				refreshTokenStore,
				mock(AdminCenterMembershipRepository.class)
		);

		service.withdraw(15L);

		verify(user).changeStatus(UserStatus.WITHDRAWN);
		verify(user).invalidateTokens();
		verify(refreshTokenStore).revokeAll(15L);
	}
}
