package com.ojimasam.safy.administrativearea.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaStatus;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaType;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;

class AdministrativeAreaQueryServiceTest {
	@Test
	void listsActiveDistrictsUnderParentArea() {
		AdministrativeAreaRepository repository = mock(AdministrativeAreaRepository.class);
		AdministrativeArea seoul = area(1L, null, "1100000000", "서울특별시", AdministrativeAreaType.SIDO);
		AdministrativeArea gangnam = area(2L, seoul, "1168000000", "강남구", AdministrativeAreaType.SIGUNGU);
		when(repository.findAllByParent_CodeAndAreaTypeAndStatusOrderByNameAsc(
				"1100000000",
				AdministrativeAreaType.SIGUNGU,
				AdministrativeAreaStatus.ACTIVE
		)).thenReturn(List.of(gangnam));
		AdministrativeAreaQueryService service = new AdministrativeAreaQueryService(repository);

		List<AdministrativeAreaResponse> result = service.list(
				AdministrativeAreaType.SIGUNGU,
				"1100000000"
		);

		assertThat(result).containsExactly(new AdministrativeAreaResponse(
				2L,
				"1168000000",
				"강남구",
				AdministrativeAreaType.SIGUNGU,
				1L,
				"1100000000"
		));
	}

	@Test
	void listsActiveAreasByTypeWhenParentCodeIsAbsent() {
		AdministrativeAreaRepository repository = mock(AdministrativeAreaRepository.class);
		when(repository.findAllByAreaTypeAndStatusOrderByNameAsc(
				AdministrativeAreaType.SIDO,
				AdministrativeAreaStatus.ACTIVE
		)).thenReturn(List.of());
		AdministrativeAreaQueryService service = new AdministrativeAreaQueryService(repository);

		service.list(AdministrativeAreaType.SIDO, null);

		verify(repository).findAllByAreaTypeAndStatusOrderByNameAsc(
				AdministrativeAreaType.SIDO,
				AdministrativeAreaStatus.ACTIVE
		);
	}

	@Test
	void rejectsMissingOrInactiveArea() {
		AdministrativeAreaRepository repository = mock(AdministrativeAreaRepository.class);
		when(repository.findByIdAndStatus(99L, AdministrativeAreaStatus.ACTIVE)).thenReturn(Optional.empty());
		AdministrativeAreaQueryService service = new AdministrativeAreaQueryService(repository);

		assertThatThrownBy(() -> service.get(99L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	private AdministrativeArea area(
			Long id,
			AdministrativeArea parent,
			String code,
			String name,
			AdministrativeAreaType areaType
	) {
		AdministrativeArea area = mock(AdministrativeArea.class);
		when(area.getId()).thenReturn(id);
		when(area.getParent()).thenReturn(parent);
		when(area.getCode()).thenReturn(code);
		when(area.getName()).thenReturn(name);
		when(area.getAreaType()).thenReturn(areaType);
		return area;
	}
}
