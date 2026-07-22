package com.ojimasam.safy.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;

public interface JpaAdminCenterMembershipRepository
		extends JpaRepository<AdminCenterMembership, Long>, AdminCenterMembershipRepository {
}
