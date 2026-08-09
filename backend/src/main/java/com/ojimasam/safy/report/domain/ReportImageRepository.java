package com.ojimasam.safy.report.domain;

import java.util.Collection;
import java.util.List;

public interface ReportImageRepository {
	ReportImage save(ReportImage reportImage);
	long countByReportId(Long reportId);
	List<ReportImage> findAllByReportIdOrderByDisplayOrderAsc(Long reportId);
	List<ReportImage> findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(Collection<Long> reportIds);
}
