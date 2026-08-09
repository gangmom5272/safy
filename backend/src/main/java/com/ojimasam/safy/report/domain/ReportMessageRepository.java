package com.ojimasam.safy.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportMessageRepository {
	ReportMessage save(ReportMessage message);
	Page<ReportMessage> findAllByReport_IdOrderBySentAtAscIdAsc(Long reportId, Pageable pageable);
}
