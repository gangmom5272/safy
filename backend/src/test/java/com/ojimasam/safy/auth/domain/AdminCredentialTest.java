package com.ojimasam.safy.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRole;

class AdminCredentialTest {
	@Test
	void locksLoginForFifteenMinutesAfterFiveFailures() {
		AdminCredential credential = new AdminCredential(
				User.operator("operator", UserRole.OPERATOR), "operator01", "hash");
		Instant now = Instant.parse("2026-07-22T06:00:00Z");

		for (int attempt = 0; attempt < 5; attempt++) credential.recordLoginFailure(now);

		assertThat(credential.getFailedLoginCount()).isEqualTo(5);
		assertThat(credential.isLoginLocked(now.plusSeconds(14 * 60))).isTrue();
		assertThat(credential.isLoginLocked(now.plusSeconds(16 * 60))).isFalse();
	}

	@Test
	void successfulLoginClearsFailuresAndLock() {
		AdminCredential credential = new AdminCredential(
				User.operator("operator", UserRole.OPERATOR), "operator01", "hash");
		Instant now = Instant.parse("2026-07-22T06:00:00Z");
		for (int attempt = 0; attempt < 5; attempt++) credential.recordLoginFailure(now);

		credential.recordLoginSuccess();

		assertThat(credential.getFailedLoginCount()).isZero();
		assertThat(credential.getLockedUntil()).isNull();
	}
}
