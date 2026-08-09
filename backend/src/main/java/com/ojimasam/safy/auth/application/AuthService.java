package com.ojimasam.safy.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.auth.application.dto.LoginResult;
import com.ojimasam.safy.auth.application.dto.TokenResponse;
import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.auth.domain.SocialAccount;
import com.ojimasam.safy.auth.domain.type.SocialProvider;
import com.ojimasam.safy.auth.domain.type.AuthErrorCode;
import com.ojimasam.safy.auth.domain.AdminCredentialRepository;
import com.ojimasam.safy.auth.domain.SocialAccountRepository;
import com.ojimasam.safy.auth.infrastructure.security.JwtTokenProvider;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore;
import com.ojimasam.safy.auth.infrastructure.security.RefreshTokenStore.RefreshTokenIdentity;
import com.ojimasam.safy.auth.application.SocialUserInfo;
import com.ojimasam.safy.auth.infrastructure.security.SocialLoginCodeStore;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.user.domain.type.UserErrorCode;
import com.ojimasam.safy.user.domain.UserRepository;

@Service
public class AuthService {
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final AdminCredentialRepository adminCredentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final SocialLoginCodeStore socialLoginCodeStore;

	public AuthService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
			AdminCredentialRepository adminCredentialRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider, RefreshTokenStore refreshTokenStore,
			SocialLoginCodeStore socialLoginCodeStore) {
		this.userRepository = userRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.adminCredentialRepository = adminCredentialRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.socialLoginCodeStore = socialLoginCodeStore;
	}

	@Transactional
	public String completeSocialLogin(String providerValue, SocialUserInfo info) {
		SocialProvider provider;
		try { provider = SocialProvider.from(providerValue); }
		catch (IllegalArgumentException exception) { throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER); }

		SocialAccount account = socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())
				.orElse(null);
		boolean newUser = account == null;
		if (newUser) {
			User user = userRepository.save(User.citizen(defaultName(info.nickname())));
			account = new SocialAccount(user, provider, info.providerUserId());
		}
		account.updateProfile(info.email(), info.nickname(), info.profileImageUrl());
		socialAccountRepository.save(account);
		validateActive(account.getUser());
		return socialLoginCodeStore.issue(account.getUser().getId(), newUser);
	}

	@Transactional(readOnly = true)
	public LoginResult exchangeSocialLoginCode(String code) {
		SocialLoginCodeStore.SocialLoginIdentity identity = socialLoginCodeStore.consume(code)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN));
		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		validateActive(user);
		return tokens(user, identity.newUser());
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public LoginResult adminLogin(String loginId, String password) {
		AdminCredential credential = adminCredentialRepository.findByLoginId(loginId)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
		Instant now = Instant.now();
		if (credential.isLoginLocked(now))
			throw new BusinessException(AuthErrorCode.LOGIN_TEMPORARILY_LOCKED);
		if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
			credential.recordLoginFailure(now);
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		validateActive(credential.getUser());
		credential.recordLoginSuccess();
		return tokens(credential.getUser(), false);
	}

	@Transactional(readOnly = true)
	public LoginResult refresh(String refreshToken) {
		RefreshTokenIdentity identity = refreshTokenStore.consume(refreshToken)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		validateActive(user);
		if (identity.tokenVersion() != user.getTokenVersion())
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		return tokens(user, false);
	}

	public void logout(String refreshToken) {
		refreshTokenStore.revoke(refreshToken);
	}

	private LoginResult tokens(User user, boolean newUser) {
		String accessToken = jwtTokenProvider.createAccessToken(user);
		String refreshToken = refreshTokenStore.issue(user.getId(), user.getTokenVersion());
		return new LoginResult(TokenResponse.of(accessToken, jwtTokenProvider.accessTokenExpiresInSeconds(), user, newUser), refreshToken);
	}

	private void validateActive(User user) {
		if (user.getStatus() == UserStatus.SUSPENDED) throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED);
		if (user.getStatus() == UserStatus.WITHDRAWN) throw new BusinessException(AuthErrorCode.ACCOUNT_WITHDRAWN);
	}

	private String defaultName(String nickname) {
		return nickname == null || nickname.isBlank() ? "SAFY 시민" : nickname;
	}
}
