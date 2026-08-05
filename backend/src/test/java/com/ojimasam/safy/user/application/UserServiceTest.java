package com.ojimasam.safy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.auth.domain.SocialAccount;
import com.ojimasam.safy.auth.domain.SocialAccountRepository;
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
		UserService service = new UserService(userRepository, refreshTokenStore, membershipRepository,
				mock(SocialAccountRepository.class), mock(ProfileImageService.class));

		var response = service.me(15L);

		assertThat(response.controlCenterId()).isEqualTo(23L);
		assertThat(response.controlCenterName()).isEqualTo("강남구 재난안전상황실");
	}

	// 이메일과 프로필 이미지는 users 가 아니라 social_accounts 에만 있다. 카카오가 http URL 을
	// 주면 https 로 서비스되는 프론트에서 mixed content 로 차단되므로 스킴을 바꿔 내려준다.
	@Test
	void meExposesSocialProfileAndRewritesHttpImageUrlToHttps() {
		UserRepository userRepository = mock(UserRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
		User user = mock(User.class);
		SocialAccount socialAccount = mock(SocialAccount.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(15L);
		when(user.getRole()).thenReturn(UserRole.CITIZEN);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(15L)).thenReturn(Optional.empty());
		when(socialAccountRepository.findFirstByUserIdOrderByLastLoginAtDesc(15L))
				.thenReturn(Optional.of(socialAccount));
		when(socialAccount.getEmail()).thenReturn("citizen@example.com");
		when(socialAccount.getProviderProfileImageUrl()).thenReturn("http://k.kakaocdn.net/dn/profile.jpg");
		UserService service = new UserService(userRepository, mock(RefreshTokenStore.class), membershipRepository,
				socialAccountRepository, mock(ProfileImageService.class));

		var response = service.me(15L);

		assertThat(response.email()).isEqualTo("citizen@example.com");
		assertThat(response.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/dn/profile.jpg");
	}

	// 소셜 계정이 없는 관리자 계정은 두 값이 모두 null 이어야 한다.
	@Test
	void meLeavesSocialProfileNullWhenAccountHasNoSocialLogin() {
		UserRepository userRepository = mock(UserRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(15L);
		when(user.getRole()).thenReturn(UserRole.OPERATOR);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(15L)).thenReturn(Optional.empty());
		when(socialAccountRepository.findFirstByUserIdOrderByLastLoginAtDesc(15L)).thenReturn(Optional.empty());
		UserService service = new UserService(userRepository, mock(RefreshTokenStore.class), membershipRepository,
				socialAccountRepository, mock(ProfileImageService.class));

		var response = service.me(15L);

		assertThat(response.email()).isNull();
		assertThat(response.profileImageUrl()).isNull();
	}

	// 직접 올린 사진이 있으면 소셜 사진 대신 그것을 준다. 되돌리기 전까지는 이 값이 이긴다.
	@Test
	void mePrefersUploadedProfileImageOverSocialImage() {
		UserRepository userRepository = mock(UserRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
		ProfileImageService profileImageService = mock(ProfileImageService.class);
		User user = mock(User.class);
		SocialAccount socialAccount = mock(SocialAccount.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(15L);
		when(user.getRole()).thenReturn(UserRole.CITIZEN);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(15L)).thenReturn(Optional.empty());
		when(socialAccountRepository.findFirstByUserIdOrderByLastLoginAtDesc(15L))
				.thenReturn(Optional.of(socialAccount));
		when(socialAccount.getProviderProfileImageUrl()).thenReturn("https://k.kakaocdn.net/dn/profile.jpg");
		when(profileImageService.findUploadedImageUrl(15L)).thenReturn("https://minio.example.com/profile/15/current");
		UserService service = new UserService(userRepository, mock(RefreshTokenStore.class), membershipRepository,
				socialAccountRepository, profileImageService);

		var response = service.me(15L);

		assertThat(response.profileImageUrl()).isEqualTo("https://minio.example.com/profile/15/current");
	}

	@Test
	void withdrawalInvalidatesAccessAndAllRefreshTokens() {
		UserRepository userRepository = mock(UserRepository.class);
		RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
		ProfileImageService profileImageService = mock(ProfileImageService.class);
		User user = mock(User.class);
		when(userRepository.findById(15L)).thenReturn(Optional.of(user));
		when(user.getRole()).thenReturn(UserRole.CITIZEN);
		UserService service = new UserService(
				userRepository,
				refreshTokenStore,
				mock(AdminCenterMembershipRepository.class),
				mock(SocialAccountRepository.class),
				profileImageService
		);

		service.withdraw(15L);

		verify(user).changeStatus(UserStatus.WITHDRAWN);
		verify(user).invalidateTokens();
		verify(refreshTokenStore).revokeAll(15L);
		// 프로필 사진은 DB 가 아니라 MinIO 에만 있으므로 탈퇴에서 함께 지워야 남지 않는다.
		verify(profileImageService).deleteUploadedImage(15L);
	}
}
