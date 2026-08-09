package com.ojimasam.safy.analysis.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository {
	RiskAssessment save(RiskAssessment assessment);
	Optional<RiskAssessment> findByReportId(Long reportId);
	List<RiskAssessment> findAllByReportIdIn(Collection<Long> reportIds);
	Optional<RiskAssessment> findByReportIdForUpdate(Long reportId);
}
