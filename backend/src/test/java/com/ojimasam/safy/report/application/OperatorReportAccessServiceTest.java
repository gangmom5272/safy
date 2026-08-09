package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.type.UserRole;

@ExtendWith(MockitoExtension.class)
class OperatorReportAccessServiceTest {
	@Mock UserRepository userRepository;
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock AdminCenterMembershipRepository membershipRepository;

	@Test
	void allowsAssignedOperatorWithActiveMatchingMembership() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		// 목 생성은 when(...) 바깥에서 끝내야 한다. thenReturn 안에서 만들면
		// 스터빙 도중 다시 스터빙이 시작돼 UnfinishedStubbingException 이 난다.
		List<ReportOperatorAssignment> assignments = List.of(assignment(20L));
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findAllByReport_IdAndOperator_Id(10L, 30L)).thenReturn(assignments);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(20L);

		assertThatNoException().isThrownBy(() -> service.requireAccess(30L, 10L));
	}

	/**
	 * 재배정 이력이 있으면 한 제보에 배정 행이 여러 건 남는다.
	 * 그중 하나라도 내 소속 관제센터와 맞으면 종결된 건까지 열어볼 수 있어야 한다.
	 */
	@Test
	void allowsOperatorWhenAnyAssignmentMatchesMembership() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		// 앞 배정은 다른 센터라 건너뛰고 뒤 배정에서 일치해야 한다.
		List<ReportOperatorAssignment> assignments = List.of(assignment(21L), assignment(20L));
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findAllByReport_IdAndOperator_Id(10L, 30L)).thenReturn(assignments);
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
		when(assignmentRepository.findAllByReport_IdAndOperator_Id(10L, 30L)).thenReturn(List.of());

		assertThatThrownBy(() -> service.requireAccess(30L, 10L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsMembershipOfAnotherControlCenter() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		List<ReportOperatorAssignment> assignments = List.of(assignment(20L));
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findAllByReport_IdAndOperator_Id(10L, 30L)).thenReturn(assignments);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(Optional.of(membership));
		when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(21L);

		assertThatThrownBy(() -> service.requireAccess(30L, 10L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsInactiveCenterMembership() {
		OperatorReportAccessService service = service();
		User operator = user(UserRole.OPERATOR);
		AdminCenterMembership membership = mock(AdminCenterMembership.class);
		// 멤버십이 INACTIVE 면 배정을 살펴보기 전에 걸러지므로 route 스터빙이 필요 없다.
		List<ReportOperatorAssignment> assignments = List.of(mock(ReportOperatorAssignment.class));
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));
		when(assignmentRepository.findAllByReport_IdAndOperator_Id(10L, 30L)).thenReturn(assignments);
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

	private ReportOperatorAssignment assignment(Long centerId) {
		ControlCenter center = mock(ControlCenter.class);
		ReportCenterRoute route = mock(ReportCenterRoute.class);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(center.getId()).thenReturn(centerId);
		when(route.getControlCenter()).thenReturn(center);
		when(assignment.getRoute()).thenReturn(route);
		return assignment;
	}
}
