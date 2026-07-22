package com.ojimasam.safy.domain.admin.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.domain.admin.dto.CreateOperatorRequest;
import com.ojimasam.safy.domain.admin.dto.OperatorResponse;
import com.ojimasam.safy.domain.admin.dto.UpdateOperatorRequest;
import com.ojimasam.safy.domain.admin.entity.AdminCenterMembership;
import com.ojimasam.safy.domain.admin.repository.AdminCenterMembershipRepository;
import com.ojimasam.safy.domain.admin.repository.ControlCenterRepository;
import com.ojimasam.safy.domain.admin.exception.AdminErrorCode;
import com.ojimasam.safy.domain.auth.entity.AdminCredential;
import com.ojimasam.safy.domain.auth.exception.AuthErrorCode;
import com.ojimasam.safy.domain.auth.repository.AdminCredentialRepository;
import com.ojimasam.safy.domain.auth.security.RefreshTokenStore;
import com.ojimasam.safy.domain.user.entity.User;
import com.ojimasam.safy.domain.user.entity.UserRole;
import com.ojimasam.safy.domain.user.entity.UserStatus;
import com.ojimasam.safy.domain.user.exception.UserErrorCode;
import com.ojimasam.safy.domain.user.repository.UserRepository;

@Service
public class AdminService {
	private final UserRepository userRepository;
	private final AdminCredentialRepository credentialRepository;
	private final AdminCenterMembershipRepository membershipRepository;
	private final ControlCenterRepository controlCenterRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenStore refreshTokenStore;

	public AdminService(UserRepository userRepository, AdminCredentialRepository credentialRepository,
			AdminCenterMembershipRepository membershipRepository, ControlCenterRepository controlCenterRepository,
			PasswordEncoder passwordEncoder, RefreshTokenStore refreshTokenStore) {
		this.userRepository = userRepository; this.credentialRepository = credentialRepository;
		this.membershipRepository = membershipRepository; this.controlCenterRepository = controlCenterRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenStore = refreshTokenStore;
	}

	@Transactional
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		AdminCredential credential = credentialRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		if (!passwordEncoder.matches(currentPassword, credential.getPasswordHash()))
			throw new BusinessException(AuthErrorCode.PASSWORD_MISMATCH);
		credential.changePassword(passwordEncoder.encode(newPassword));
		credential.getUser().invalidateTokens();
		refreshTokenStore.revokeAll(userId);
	}

	@Transactional
	public OperatorResponse create(CreateOperatorRequest request) {
		if (credentialRepository.existsByLoginId(request.loginId()))
			throw new BusinessException(AuthErrorCode.LOGIN_ID_DUPLICATED);
		validateControlCenter(request.controlCenterId());
		User user = userRepository.save(User.operator(request.displayName(), UserRole.OPERATOR));
		AdminCredential credential = credentialRepository.save(
				new AdminCredential(user, request.loginId(), passwordEncoder.encode(request.password())));
		AdminCenterMembership membership = membershipRepository.save(
				new AdminCenterMembership(user, request.controlCenterId(), request.membershipRole()));
		return OperatorResponse.of(user, credential, membership);
	}

	@Transactional(readOnly = true)
	public PageResponse<OperatorResponse> list(Pageable pageable) {
		Page<OperatorResponse> page = userRepository.findByRoleIn(
				List.of(UserRole.OPERATOR, UserRole.SUPER_ADMIN), pageable).map(this::response);
		return PageResponse.from(page);
	}

	@Transactional(readOnly = true)
	public OperatorResponse get(Long operatorId) { return response(operator(operatorId)); }

	@Transactional
	public OperatorResponse update(Long operatorId, UpdateOperatorRequest request) {
		validateControlCenter(request.controlCenterId());
		User user = operator(operatorId);
		user.updateDisplayName(request.displayName());
		AdminCenterMembership membership = membershipRepository.findByUserIdAndIsPrimaryTrue(operatorId)
				.orElseGet(() -> membershipRepository.save(
						new AdminCenterMembership(user, request.controlCenterId(), request.membershipRole())));
		membership.update(request.controlCenterId(), request.membershipRole());
		return response(user);
	}

	@Transactional
	public OperatorResponse changeStatus(Long operatorId, UserStatus status) {
		if (status != UserStatus.ACTIVE && status != UserStatus.SUSPENDED)
			throw new BusinessException(UserErrorCode.INVALID_USER_STATUS);
		User user = operator(operatorId);
		if (user.getRole() == UserRole.SUPER_ADMIN)
			throw new BusinessException(UserErrorCode.INVALID_USER_STATUS, "슈퍼 관리자 상태는 변경할 수 없습니다.");
		user.changeStatus(status);
		if (status == UserStatus.SUSPENDED) {
			user.invalidateTokens();
			refreshTokenStore.revokeAll(operatorId);
		}
		return response(user);
	}

	private User operator(Long id) {
		User user = userRepository.findById(id).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		if (user.getRole() == UserRole.CITIZEN) throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
		return user;
	}

	private OperatorResponse response(User user) {
		AdminCredential credential = credentialRepository.findById(user.getId())
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		AdminCenterMembership membership = membershipRepository.findByUserIdAndIsPrimaryTrue(user.getId()).orElse(null);
		return OperatorResponse.of(user, credential, membership);
	}

	private void validateControlCenter(Long centerId) {
		if (!controlCenterRepository.existsById(centerId))
			throw new BusinessException(AdminErrorCode.CONTROL_CENTER_NOT_FOUND);
	}
}
