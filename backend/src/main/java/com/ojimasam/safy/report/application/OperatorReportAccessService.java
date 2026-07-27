package com.ojimasam.safy.report.application;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.UserRole;

@Service
public class OperatorReportAccessService {
	public static final Set<ReportOperatorAssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES = Set.of(
			ReportOperatorAssignmentStatus.ASSIGNED,
			ReportOperatorAssignmentStatus.ACCEPTED,
			ReportOperatorAssignmentStatus.IN_PROGRESS
	);

	private final UserRepository userRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final AdminCenterMembershipRepository membershipRepository;

	public OperatorReportAccessService(UserRepository userRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			AdminCenterMembershipRepository membershipRepository) {
		this.userRepository = userRepository;
		this.assignmentRepository = assignmentRepository;
		this.membershipRepository = membershipRepository;
	}

	@Transactional(readOnly = true)
	public void requireAccess(Long userId, Long reportId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
		if (user.getRole() == UserRole.SUPER_ADMIN) return;
		if (user.getRole() != UserRole.OPERATOR) {
			throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
		}

		ReportOperatorAssignment assignment = assignmentRepository
				.findByReport_IdAndStatusIn(reportId, ACTIVE_ASSIGNMENT_STATUSES)
				.filter(found -> found.getOperator().getId().equals(userId))
				.orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
		boolean activeMembership = membershipRepository.findByUserIdAndIsPrimaryTrue(userId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.filter(membership -> membership.getControlCenterId()
						.equals(assignment.getRoute().getControlCenter().getId()))
				.isPresent();
		if (!activeMembership) throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
	}

	@Transactional(readOnly = true)
	public boolean isSuperAdmin(Long userId) {
		return userRepository.findById(userId)
				.map(User::getRole)
				.filter(role -> role == UserRole.SUPER_ADMIN)
				.isPresent();
	}

	@Transactional(readOnly = true)
	public void requireActiveOperator(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
		if (user.getRole() != UserRole.OPERATOR) {
			throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
		}
		boolean activeMembership = membershipRepository.findByUserIdAndIsPrimaryTrue(userId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.isPresent();
		if (!activeMembership) throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
	}
}
