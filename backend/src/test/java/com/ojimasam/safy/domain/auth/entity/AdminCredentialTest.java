package com.ojimasam.safy.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.domain.user.entity.User;
import com.ojimasam.safy.domain.user.entity.UserRole;

class AdminCredentialTest {
	@Test
	void locksLoginForFifteenMinutesAfterFiveFailures() {
		AdminCredential credential = new AdminCredential(
				User.operator("operator", UserRole.OPERATOR), "operator01", "hash");
		LocalDateTime now = LocalDateTime.now();

		for (int attempt = 0; attempt < 5; attempt++) credential.recordLoginFailure(now);

		assertThat(credential.getFailedLoginCount()).isEqualTo(5);
		assertThat(credential.isLoginLocked(now.plusMinutes(14))).isTrue();
		assertThat(credential.isLoginLocked(now.plusMinutes(16))).isFalse();
	}

	@Test
	void successfulLoginClearsFailuresAndLock() {
		AdminCredential credential = new AdminCredential(
				User.operator("operator", UserRole.OPERATOR), "operator01", "hash");
		LocalDateTime now = LocalDateTime.now();
		for (int attempt = 0; attempt < 5; attempt++) credential.recordLoginFailure(now);

		credential.recordLoginSuccess();

		assertThat(credential.getFailedLoginCount()).isZero();
		assertThat(credential.getLockedUntil()).isNull();
	}
}
