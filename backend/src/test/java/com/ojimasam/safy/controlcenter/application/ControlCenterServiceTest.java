package com.ojimasam.safy.controlcenter.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.type.MembershipStatus;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterErrorCode;
import com.ojimasam.safy.controlcenter.domain.ControlCenterRepository;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;
import com.ojimasam.safy.controlcenter.application.dto.CreateControlCenterRequest;

class ControlCenterServiceTest {
	@Test
	void rejectsDuplicatedCenterCode() {
		ControlCenterRepository centerRepository = mock(ControlCenterRepository.class);
		when(centerRepository.existsByCode("CC-1168000000")).thenReturn(true);
		ControlCenterService service = service(centerRepository, mock(AdminCenterMembershipRepository.class));
		CreateControlCenterRequest request = new CreateControlCenterRequest(
				"CC-1168000000",
				"강남구 재난안전상황실",
				com.ojimasam.safy.controlcenter.domain.type.ControlCenterType.DISTRICT,
				null,
				null,
				null
		);

		assertThatThrownBy(() -> service.create(request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
								.isEqualTo(ControlCenterErrorCode.CONTROL_CENTER_CODE_DUPLICATED));

		verify(centerRepository, never()).save(any());
	}

	@Test
	void doesNotDeactivateCenterWithActiveOperator() {
		ControlCenterRepository centerRepository = mock(ControlCenterRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		ControlCenter center = mock(ControlCenter.class);
		when(centerRepository.findById(10L)).thenReturn(Optional.of(center));
		when(membershipRepository.existsByControlCenter_IdAndStatus(10L, MembershipStatus.ACTIVE))
				.thenReturn(true);
		ControlCenterService service = service(centerRepository, membershipRepository);

		assertThatThrownBy(() -> service.deactivate(10L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
								.isEqualTo(ControlCenterErrorCode.CONTROL_CENTER_HAS_ACTIVE_OPERATOR));

		verify(center, never()).deactivate();
	}

	@Test
	void deactivatesCoverageTogetherWithCenter() {
		ControlCenterRepository centerRepository = mock(ControlCenterRepository.class);
		ControlCenterAreaRepository areaRepository = mock(ControlCenterAreaRepository.class);
		AdminCenterMembershipRepository membershipRepository = mock(AdminCenterMembershipRepository.class);
		ControlCenter center = mock(ControlCenter.class);
		ControlCenterArea coverage = mock(ControlCenterArea.class);
		when(centerRepository.findById(10L)).thenReturn(Optional.of(center));
		when(membershipRepository.existsByControlCenter_IdAndStatus(10L, MembershipStatus.ACTIVE))
				.thenReturn(false);
		when(areaRepository.findAllByControlCenter_IdOrderByPriorityAscAdministrativeArea_NameAsc(10L))
				.thenReturn(List.of(coverage));
		when(coverage.getStatus()).thenReturn(CoverageStatus.ACTIVE);
		ControlCenterService service = new ControlCenterService(
				centerRepository,
				areaRepository,
				mock(AdministrativeAreaRepository.class),
				membershipRepository
		);

		service.deactivate(10L);

		verify(coverage).deactivate(any());
		verify(center).deactivate();
	}

	private ControlCenterService service(
			ControlCenterRepository centerRepository,
			AdminCenterMembershipRepository membershipRepository
	) {
		return new ControlCenterService(
				centerRepository,
				mock(ControlCenterAreaRepository.class),
				mock(AdministrativeAreaRepository.class),
				membershipRepository
		);
	}
}
