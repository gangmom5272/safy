package com.ojimasam.safy.report.domain;

import java.util.List;

public interface ReportStatusHistoryRepository {
	ReportStatusHistory save(ReportStatusHistory history);
	List<ReportStatusHistory> findAllByReport_IdOrderByChangedAtAscIdAsc(Long reportId);
}
