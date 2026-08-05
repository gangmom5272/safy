package com.ojimasam.safy.report.domain;

import java.util.List;

import com.ojimasam.safy.report.domain.type.ReportStatus;

public interface ReportStatusHistoryRepository {
	ReportStatusHistory save(ReportStatusHistory history);
	List<ReportStatusHistory> findAllByReport_IdOrderByChangedAtAscIdAsc(Long reportId);
	boolean existsByReport_IdAndToStatus(Long reportId, ReportStatus status);
}
