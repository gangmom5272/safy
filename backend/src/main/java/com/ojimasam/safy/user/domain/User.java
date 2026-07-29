package com.ojimasam.safy.user.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 255)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@Column(nullable = false)
	private long tokenVersion;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(columnDefinition = "timestamp(6) with time zone")
	private Instant withdrawnAt;

	protected User() {
	}

	private User(String displayName, UserRole role) {
		this.displayName = displayName;
		this.role = role;
		this.status = UserStatus.ACTIVE;
	}

	public static User citizen(String displayName) {
		return new User(displayName, UserRole.CITIZEN);
	}

	public static User operator(String displayName, UserRole role) {
		if (role != UserRole.OPERATOR && role != UserRole.SUPER_ADMIN) {
			throw new IllegalArgumentException("Administrator role is required");
		}
		return new User(displayName, role);
	}

	public void updateDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void changeStatus(UserStatus status) {
		this.status = status;
		if (status == UserStatus.WITHDRAWN) {
			this.withdrawnAt = Instant.now();
		}
	}

	public void invalidateTokens() {
		this.tokenVersion++;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public Long getId() { return id; }
	public String getDisplayName() { return displayName; }
	public UserRole getRole() { return role; }
	public UserStatus getStatus() { return status; }
	public long getTokenVersion() { return tokenVersion; }
	public Instant getWithdrawnAt() { return withdrawnAt; }
}
