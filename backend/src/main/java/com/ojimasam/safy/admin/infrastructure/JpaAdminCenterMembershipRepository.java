package com.ojimasam.safy.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.type.UserStatus;

import jakarta.persistence.LockModeType;

public interface JpaAdminCenterMembershipRepository
		extends JpaRepository<AdminCenterMembership, Long>, AdminCenterMembershipRepository {
	@Override
	@EntityGraph(attributePaths = {"user", "controlCenter"})
	java.util.Optional<AdminCenterMembership> findByUserIdAndIsPrimaryTrue(Long userId);

	@Override
	@EntityGraph(attributePaths = {"user", "controlCenter"})
	org.springframework.data.domain.Page<AdminCenterMembership> findAllByControlCenter_IdAndStatus(
			Long controlCenterId,
			com.ojimasam.safy.admin.domain.type.MembershipStatus status,
			org.springframework.data.domain.Pageable pageable
	);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select membership
			from AdminCenterMembership membership
			join fetch membership.user operator
			where membership.controlCenter.id = :controlCenterId
			  and membership.status = :membershipStatus
			  and operator.role = :userRole
			  and operator.status = :userStatus
			order by operator.id asc
			""")
	java.util.List<AdminCenterMembership> findEligibleOperatorsForAssignment(
			@Param("controlCenterId") Long controlCenterId,
			@Param("membershipStatus") MembershipStatus membershipStatus,
			@Param("userRole") UserRole userRole,
			@Param("userStatus") UserStatus userStatus
	);
}
