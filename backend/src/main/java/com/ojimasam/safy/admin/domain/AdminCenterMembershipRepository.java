package com.ojimasam.safy.admin.domain;

import java.util.Optional;

public interface AdminCenterMembershipRepository {
	AdminCenterMembership save(AdminCenterMembership membership);
	Optional<AdminCenterMembership> findByUserIdAndIsPrimaryTrue(Long userId);
}
