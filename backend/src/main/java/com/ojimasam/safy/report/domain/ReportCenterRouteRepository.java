package com.ojimasam.safy.report.domain;

import java.util.Optional;

public interface ReportCenterRouteRepository {
	ReportCenterRoute save(ReportCenterRoute route);

	long countByControlCenter_IdAndStatus(Long controlCenterId, ReportCenterRouteStatus status);

	Optional<ReportCenterRoute> findByReport_IdAndStatus(Long reportId, ReportCenterRouteStatus status);

	Optional<ReportCenterRoute> findTopByReport_IdOrderByRoutedAtDesc(Long reportId);
}
