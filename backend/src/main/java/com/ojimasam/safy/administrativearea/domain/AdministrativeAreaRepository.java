package com.ojimasam.safy.administrativearea.domain;

import java.util.List;
import java.util.Optional;

public interface AdministrativeAreaRepository {
	Optional<AdministrativeArea> findByIdAndStatus(Long id, AdministrativeAreaStatus status);
	Optional<AdministrativeArea> findByCodeAndStatus(String code, AdministrativeAreaStatus status);

	List<AdministrativeArea> findAllByAreaTypeAndStatusOrderByNameAsc(
			AdministrativeAreaType areaType,
			AdministrativeAreaStatus status
	);

	List<AdministrativeArea> findAllByParent_CodeAndAreaTypeAndStatusOrderByNameAsc(
			String parentCode,
			AdministrativeAreaType areaType,
			AdministrativeAreaStatus status
	);
}
