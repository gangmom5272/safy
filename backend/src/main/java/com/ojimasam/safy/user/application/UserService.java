package com.ojimasam.safy.user.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.user.application.dto.UserResponse;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.user.domain.type.UserErrorCode;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.auth.domain.SocialAccountRepository;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;
import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final RefreshTokenStore refreshTokenStore;
	private final AdminCenterMembershipRepository membershipRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final ProfileImageService profileImageService;
	public UserService(UserRepository userRepository, RefreshTokenStore refreshTokenStore,
			AdminCenterMembershipRepository membershipRepository,
			SocialAccountRepository socialAccountRepository,
			ProfileImageService profileImageService) {
		this.userRepository = userRepository;
		this.refreshTokenStore = refreshTokenStore;
		this.membershipRepository = membershipRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.profileImageService = profileImageService;
	}

	@Transactional(readOnly = true)
	public UserResponse me(Long userId) { return response(get(userId)); }

	@Transactional
	public UserResponse updateName(Long userId, String displayName) {
		User user = get(userId);
		user.updateDisplayName(displayName);
		return response(user);
	}

	@Transactional
	public void withdraw(Long userId) {
		User user = get(userId);
		if (user.getRole() != UserRole.CITIZEN) throw new BusinessException(UserErrorCode.CITIZEN_ONLY_OPERATION);
		user.changeStatus(UserStatus.WITHDRAWN);
		user.invalidateTokens();
		refreshTokenStore.revokeAll(userId);
		// 직접 올린 프로필 사진은 DB 가 아니라 MinIO 에만 있으므로 여기서 함께 지운다.
		profileImageService.deleteUploadedImage(userId);
	}

	private User get(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}

	private UserResponse response(User user) {
		AdminCenterMembership membership = membershipRepository
				.findByUserIdAndIsPrimaryTrue(user.getId())
				.orElse(null);
		// 이메일과 소셜 프로필 사진은 users 가 아니라 social_accounts 에만 있고, 직접 올린
		// 프로필 사진은 MinIO 에만 있다. 둘을 여기서 합쳐 응답 한 곳에서만 우선순위를 정한다.
		return UserResponse.from(user, membership, socialAccountRepository
				.findFirstByUserIdOrderByLastLoginAtDesc(user.getId())
				.orElse(null),
				profileImageService.findUploadedImageUrl(user.getId()));
	}
}
