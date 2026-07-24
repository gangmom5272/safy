package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;

public interface JpaReportStatusHistoryRepository
		extends JpaRepository<ReportStatusHistory, Long>, ReportStatusHistoryRepository {
	@Override
	@EntityGraph(attributePaths = "changedBy")
	java.util.List<ReportStatusHistory> findAllByReport_IdOrderByChangedAtAscIdAsc(Long reportId);
}
