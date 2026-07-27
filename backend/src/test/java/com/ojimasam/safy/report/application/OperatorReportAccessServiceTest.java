package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class OperatorReportAccessServiceTest {
	@Mock UserRepository userRepository;
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock AdminCenterMembershipRepository membershipRepository;

	@Test
	void allowsAssignedOperatorWithActiveMatchingMembership() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		when(operator.getId()).thenReturn(30L);
		ReportOperatorAssignment assignment = assignment(operator, 20L);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findByReport_IdAndStatusIn(
				org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.of(assignment));
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(20L);

		assertThatNoException().isThrownBy(() -> service.requireAccess(30L, 10L));
	}

	@Test
	void rejectsOperatorAssignedToAnotherReport() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findByReport_IdAndStatusIn(
				org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.requireAccess(30L, 10L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsInactiveCenterMembership() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		when(operator.getId()).thenReturn(30L);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(assignment.getOperator()).thenReturn(operator);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findByReport_IdAndStatusIn(
				org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.of(assignment));
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(membership.getStatus()).thenReturn(MembershipStatus.INACTIVE);

		assertThatThrownBy(() -> service.requireAccess(30L, 10L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void superAdminBypassesAssignmentAndMembership() {
		OperatorReportAccessService service = service();
		User superAdmin = user(UserRole.SUPER_ADMIN);
		when(userRepository.findById(99L)).thenReturn(Optional.of(superAdmin));

		assertThatNoException().isThrownBy(() -> service.requireAccess(99L, 10L));
	}

	private OperatorReportAccessService service() {
		return new OperatorReportAccessService(userRepository, assignmentRepository, membershipRepository);
	}

	private User user(UserRole role) {
		User user = mock(User.class);
		when(user.getRole()).thenReturn(role);
		return user;
	}

	private ReportOperatorAssignment assignment(User operator, Long centerId) {
		ControlCenter center = mock(ControlCenter.class);
		ReportCenterRoute route = mock(ReportCenterRoute.class);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(center.getId()).thenReturn(centerId);
		when(route.getControlCenter()).thenReturn(center);
		when(assignment.getOperator()).thenReturn(operator);
		when(assignment.getRoute()).thenReturn(route);
		return assignment;
	}
}
