package com.ojimasam.safy.admin.domain;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;

public interface AdminCenterMembershipRepository {
	AdminCenterMembership save(AdminCenterMembership membership);
	Optional<AdminCenterMembership> findByUserIdAndIsPrimaryTrue(Long userId);
	Page<AdminCenterMembership> findAllByControlCenter_IdAndStatus(
			Long controlCenterId,
			MembershipStatus status,
			Pageable pageable
	);
	boolean existsByControlCenter_IdAndStatus(Long controlCenterId, MembershipStatus status);
	/** 배정 대상을 고르기 위한 조회. 동시 배정을 직렬화하려고 행을 잠근다. */
	List<AdminCenterMembership> findEligibleOperatorsForAssignment(
			Long controlCenterId,
			MembershipStatus membershipStatus,
			UserRole userRole,
			UserStatus userStatus
	);

	/**
	 * 센터에 배정 가능한 담당자가 있는지 확인하기 위한 조회. 후보를 고르지 않으므로 잠그지 않는다.
	 * 잠그면 읽기 전용 트랜잭션에서 PostgreSQL 이 SELECT ... FOR UPDATE 를 거부한다.
	 */
	List<Long> findAssignableOperatorIds(
			Long controlCenterId,
			MembershipStatus membershipStatus,
			UserRole userRole,
			UserStatus userStatus
	);
}
