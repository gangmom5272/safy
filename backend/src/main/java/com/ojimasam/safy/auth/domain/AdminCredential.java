package com.ojimasam.safy.auth.domain;

import java.time.LocalDateTime;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "admin_credentials")
public class AdminCredential extends BaseEntity {
	private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
	private static final long LOGIN_LOCK_MINUTES = 15;

	@Id
	@Column(name = "user_id")
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false, unique = true, length = 100)
	private String loginId;

	@Column(nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false)
	private long failedLoginCount;

	private LocalDateTime lockedUntil;

	@Version
	private long version;

	private LocalDateTime passwordChangedAt;
	private LocalDateTime lastLoginAt;

	protected AdminCredential() {
	}

	public AdminCredential(User user, String loginId, String passwordHash) {
		this.user = user;
		this.loginId = loginId;
		this.passwordHash = passwordHash;
		this.passwordChangedAt = LocalDateTime.now();
	}

	public boolean isLoginLocked(LocalDateTime now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}
	public void recordLoginSuccess() {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
		this.lastLoginAt = LocalDateTime.now();
	}
	public void recordLoginFailure(LocalDateTime now) {
		this.failedLoginCount++;
		if (failedLoginCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
			this.lockedUntil = now.plusMinutes(LOGIN_LOCK_MINUTES);
		}
	}
	public void changePassword(String hash) { this.passwordHash = hash; this.passwordChangedAt = LocalDateTime.now(); }
	public User getUser() { return user; }
	public String getLoginId() { return loginId; }
	public String getPasswordHash() { return passwordHash; }
	public LocalDateTime getLastLoginAt() { return lastLoginAt; }
	public long getFailedLoginCount() { return failedLoginCount; }
	public LocalDateTime getLockedUntil() { return lockedUntil; }
}
