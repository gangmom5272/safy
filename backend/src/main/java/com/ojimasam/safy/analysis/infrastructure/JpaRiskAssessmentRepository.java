package com.ojimasam.safy.analysis.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;

import jakarta.persistence.LockModeType;

public interface JpaRiskAssessmentRepository
		extends JpaRepository<RiskAssessment, Long>, RiskAssessmentRepository {
	@Override
	List<RiskAssessment> findAllByReportIdIn(Collection<Long> reportIds);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select assessment from RiskAssessment assessment where assessment.reportId = :reportId")
	Optional<RiskAssessment> findByReportIdForUpdate(@Param("reportId") Long reportId);
}
