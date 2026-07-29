package com.ojimasam.safy.report.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.type.UserRole;

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

	/**
	 * 조회 권한을 판단한다. 담당자는 자신이 맡았던 제보라면 종결(COMPLETED/CANCELLED)된 건도
	 * 다시 열어볼 수 있다. 종료·반려·재배정 같은 처리 액션은 별도로
	 * {@code requireOwnedActiveAssignment} 가 진행 중 배정만 허용하므로 여기서 넓혀도 안전하다.
	 */
	@Transactional(readOnly = true)
	public void requireAccess(Long userId, Long reportId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
		if (user.getRole() == UserRole.SUPER_ADMIN) return;
		if (user.getRole() != UserRole.OPERATOR) {
			throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
		}

		// 재배정 이력이 있으면 한 제보에 내 배정이 여러 건일 수 있어 목록으로 조회한다.
		List<ReportOperatorAssignment> assignments =
				assignmentRepository.findAllByReport_IdAndOperator_Id(reportId, userId);
		if (assignments.isEmpty()) throw new BusinessException(CommonErrorCode.ACCESS_DENIED);

		boolean activeMembership = membershipRepository.findByUserIdAndIsPrimaryTrue(userId)
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.filter(membership -> assignments.stream().anyMatch(assignment -> membership.getControlCenterId()
						.equals(assignment.getRoute().getControlCenter().getId())))
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
