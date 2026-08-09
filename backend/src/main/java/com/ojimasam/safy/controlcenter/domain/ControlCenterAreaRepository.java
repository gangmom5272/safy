package com.ojimasam.safy.controlcenter.domain;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;

public interface ControlCenterAreaRepository {
	ControlCenterArea save(ControlCenterArea controlCenterArea);

	Optional<ControlCenterArea> findByIdAndControlCenter_Id(Long id, Long controlCenterId);

	List<ControlCenterArea> findAllByControlCenter_IdOrderByPriorityAscAdministrativeArea_NameAsc(Long controlCenterId);

	boolean existsByControlCenter_IdAndAdministrativeArea_IdAndCoverageRole(
			Long controlCenterId,
			Long administrativeAreaId,
			CoverageRole coverageRole
	);

	List<ControlCenterArea> findRoutingCandidates(
			Long administrativeAreaId,
			Instant routedAt,
			CoverageStatus coverageStatus,
			com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus centerStatus,
			CoverageRole primaryRole
	);
}
