package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;

public interface JpaReportCenterRouteRepository
		extends JpaRepository<ReportCenterRoute, Long>, ReportCenterRouteRepository {
	@Override
	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = "controlCenter")
	java.util.Optional<ReportCenterRoute> findByReport_IdAndStatus(
			Long reportId,
			com.ojimasam.safy.report.domain.ReportCenterRouteStatus status
	);

	@Override
	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = "controlCenter")
	java.util.Optional<ReportCenterRoute> findTopByReport_IdOrderByRoutedAtDesc(Long reportId);
}
