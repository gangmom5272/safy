package com.ojimasam.safy.admin.domain;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ojimasam.safy.user.domain.UserRole;
import com.ojimasam.safy.user.domain.UserStatus;

public interface AdminCenterMembershipRepository {
	AdminCenterMembership save(AdminCenterMembership membership);
	Optional<AdminCenterMembership> findByUserIdAndIsPrimaryTrue(Long userId);
	Page<AdminCenterMembership> findAllByControlCenter_IdAndStatus(
			Long controlCenterId,
			MembershipStatus status,
			Pageable pageable
	);
	boolean existsByControlCenter_IdAndStatus(Long controlCenterId, MembershipStatus status);
	List<AdminCenterMembership> findEligibleOperatorsForAssignment(
			Long controlCenterId,
			MembershipStatus membershipStatus,
			UserRole userRole,
			UserStatus userStatus
	);
}
