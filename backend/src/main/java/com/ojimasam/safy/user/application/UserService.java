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
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final RefreshTokenStore refreshTokenStore;
	public UserService(UserRepository userRepository, RefreshTokenStore refreshTokenStore) {
		this.userRepository = userRepository;
		this.refreshTokenStore = refreshTokenStore;
	}

	@Transactional(readOnly = true)
	public UserResponse me(Long userId) { return UserResponse.from(get(userId)); }

	@Transactional
	public UserResponse updateName(Long userId, String displayName) {
		User user = get(userId);
		user.updateDisplayName(displayName);
		return UserResponse.from(user);
	}

	@Transactional
	public void withdraw(Long userId) {
		User user = get(userId);
		if (user.getRole() != UserRole.CITIZEN) throw new BusinessException(UserErrorCode.CITIZEN_ONLY_OPERATION);
		user.changeStatus(UserStatus.WITHDRAWN);
		user.invalidateTokens();
		refreshTokenStore.revokeAll(userId);
	}

	private User get(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}
}
