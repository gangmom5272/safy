package com.ojimasam.safy.admin.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "admin_center_memberships",
		indexes = {
				@Index(name = "idx_admin_memberships_user_primary", columnList = "user_id, is_primary"),
				@Index(name = "idx_admin_memberships_center_status", columnList = "control_center_id, status")
		}
)
public class AdminCenterMembership extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "control_center_id", nullable = false)
	private ControlCenter controlCenter;

	@Column(nullable = false, length = 30)
	private String membershipRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MembershipStatus status;

	@Column(nullable = false)
	private boolean isPrimary;

	@Column(nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant startedAt;

	@Column(columnDefinition = "timestamp(6) with time zone")
	private Instant endedAt;

	protected AdminCenterMembership() {
	}

	public AdminCenterMembership(User user, ControlCenter controlCenter, String membershipRole) {
		if (user == null) throw new IllegalArgumentException("관리자는 필수입니다.");
		if (controlCenter == null) throw new IllegalArgumentException("관제센터는 필수입니다.");
		validateMembershipRole(membershipRole);
		this.user = user;
		this.controlCenter = controlCenter;
		this.membershipRole = membershipRole.trim();
		this.status = MembershipStatus.ACTIVE;
		this.isPrimary = true;
		this.startedAt = Instant.now();
	}

	public void changeRole(String membershipRole) {
		validateMembershipRole(membershipRole);
		this.membershipRole = membershipRole.trim();
	}

	public void end(Instant endedAt) {
		if (endedAt == null || endedAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("소속 종료 시각이 올바르지 않습니다.");
		}
		this.status = MembershipStatus.INACTIVE;
		this.isPrimary = false;
		this.endedAt = endedAt;
	}

	private void validateMembershipRole(String membershipRole) {
		if (membershipRole == null || membershipRole.isBlank()) {
			throw new IllegalArgumentException("관제센터 담당 역할은 필수입니다.");
		}
		if (membershipRole.length() > 30) {
			throw new IllegalArgumentException("관제센터 담당 역할은 30자 이하여야 합니다.");
		}
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public ControlCenter getControlCenter() {
		return controlCenter;
	}

	public Long getControlCenterId() {
		return controlCenter.getId();
	}

	public String getMembershipRole() {
		return membershipRole;
	}

	public MembershipStatus getStatus() {
		return status;
	}

	public boolean isPrimary() {
		return isPrimary;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}
}
