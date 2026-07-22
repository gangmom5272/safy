package com.ojimasam.safy.domain.admin.entity;

import java.time.LocalDateTime;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_center_memberships")
public class AdminCenterMembership extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	@Column(name = "control_center_id", nullable = false)
	private Long controlCenterId;
	@Column(nullable = false, length = 30)
	private String membershipRole;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
	private MembershipStatus status;
	@Column(nullable = false)
	private boolean isPrimary;
	@Column(nullable = false)
	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	protected AdminCenterMembership() {}
	public AdminCenterMembership(User user, Long centerId, String membershipRole) {
		this.user = user; this.controlCenterId = centerId; this.membershipRole = membershipRole;
		this.status = MembershipStatus.ACTIVE; this.isPrimary = true; this.startedAt = LocalDateTime.now();
	}
	public void update(Long centerId, String role) { this.controlCenterId = centerId; this.membershipRole = role; }
	public Long getControlCenterId() { return controlCenterId; }
	public String getMembershipRole() { return membershipRole; }
}
