package com.ojimasam.safy.report.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportRepository {
	Report save(Report report);
	Optional<Report> findByIdAndReporterId(Long id, Long reporterId);
	Page<Report> findAllByReporterId(Long reporterId, Pageable pageable);
}
