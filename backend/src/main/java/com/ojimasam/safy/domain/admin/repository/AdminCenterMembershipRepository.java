package com.ojimasam.safy.domain.admin.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.domain.admin.entity.AdminCenterMembership;

public interface AdminCenterMembershipRepository extends JpaRepository<AdminCenterMembership, Long> {
	Optional<AdminCenterMembership> findByUserIdAndIsPrimaryTrue(Long userId);
}
