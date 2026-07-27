package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.report.domain.ReportMessage;
import com.ojimasam.safy.report.domain.ReportMessageRepository;

public interface JpaReportMessageRepository extends JpaRepository<ReportMessage, Long>, ReportMessageRepository {
	@Override
	@EntityGraph(attributePaths = "report")
	Page<ReportMessage> findAllByReport_IdOrderBySentAtAscIdAsc(Long reportId, Pageable pageable);
}
