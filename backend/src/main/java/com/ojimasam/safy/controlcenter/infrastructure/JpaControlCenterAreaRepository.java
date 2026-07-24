package com.ojimasam.safy.controlcenter.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.CoverageStatus;

import java.time.Instant;

public interface JpaControlCenterAreaRepository
		extends JpaRepository<ControlCenterArea, Long>, ControlCenterAreaRepository {
	@Override
	@EntityGraph(attributePaths = {"controlCenter", "administrativeArea"})
	java.util.List<ControlCenterArea> findAllByControlCenter_IdOrderByPriorityAscAdministrativeArea_NameAsc(
			Long controlCenterId
	);

	@Override
	@EntityGraph(attributePaths = {"controlCenter", "administrativeArea"})
	java.util.Optional<ControlCenterArea> findByIdAndControlCenter_Id(Long id, Long controlCenterId);

	@Override
	@EntityGraph(attributePaths = {"controlCenter", "administrativeArea"})
	@Query("""
			select coverage
			from ControlCenterArea coverage
			join coverage.controlCenter center
			where coverage.administrativeArea.id = :administrativeAreaId
			  and coverage.status = :coverageStatus
			  and center.status = :centerStatus
			  and coverage.effectiveFrom <= :routedAt
			  and (coverage.effectiveTo is null or coverage.effectiveTo > :routedAt)
			order by
			  case when coverage.coverageRole = :primaryRole then 0 else 1 end,
			  coverage.priority asc,
			  coverage.id asc
			""")
	java.util.List<ControlCenterArea> findRoutingCandidates(
			@Param("administrativeAreaId") Long administrativeAreaId,
			@Param("routedAt") Instant routedAt,
			@Param("coverageStatus") CoverageStatus coverageStatus,
			@Param("centerStatus") ControlCenterStatus centerStatus,
			@Param("primaryRole") CoverageRole primaryRole
	);
}
