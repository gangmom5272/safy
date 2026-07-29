package com.ojimasam.safy.administrativearea.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaType;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.administrativearea.application.dto.AdministrativeAreaResponse;

@Service
@Transactional(readOnly = true)
public class AdministrativeAreaQueryService {
	private final AdministrativeAreaRepository administrativeAreaRepository;

	public AdministrativeAreaQueryService(AdministrativeAreaRepository administrativeAreaRepository) {
		this.administrativeAreaRepository = administrativeAreaRepository;
	}

	public List<AdministrativeAreaResponse> list(AdministrativeAreaType areaType, String parentCode) {
		List<AdministrativeArea> areas = parentCode == null || parentCode.isBlank()
				? administrativeAreaRepository.findAllByAreaTypeAndStatusOrderByNameAsc(
						areaType,
						AdministrativeAreaStatus.ACTIVE
				)
				: administrativeAreaRepository.findAllByParent_CodeAndAreaTypeAndStatusOrderByNameAsc(
						parentCode.trim(),
						areaType,
						AdministrativeAreaStatus.ACTIVE
				);

		return areas.stream().map(AdministrativeAreaResponse::from).toList();
	}

	public AdministrativeAreaResponse get(Long areaId) {
		return administrativeAreaRepository.findByIdAndStatus(areaId, AdministrativeAreaStatus.ACTIVE)
				.map(AdministrativeAreaResponse::from)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}
}
