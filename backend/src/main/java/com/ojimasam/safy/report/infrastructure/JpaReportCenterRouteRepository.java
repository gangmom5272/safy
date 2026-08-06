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
			com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus status
	);

	@Override
	@org.springframework.data.jpa.repository.EntityGraph(attributePaths = "controlCenter")
	java.util.Optional<ReportCenterRoute> findTopByReport_IdOrderByRoutedAtDesc(Long reportId);

	// 이관 사건은 "어디서 어디로"가 한 줄이라 이전 라우트의 센터까지 함께 읽는다.
	@Override
	@org.springframework.data.jpa.repository.EntityGraph(
			attributePaths = {"controlCenter", "previousRoute", "previousRoute.controlCenter"})
	java.util.List<ReportCenterRoute> findAllByReport_IdOrderByRoutedAtAscIdAsc(Long reportId);
}
