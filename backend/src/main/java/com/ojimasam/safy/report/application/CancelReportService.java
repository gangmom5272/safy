package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class CancelReportService {
	private final ReportRepository reportRepository;

	public CancelReportService(ReportRepository reportRepository) {
		this.reportRepository = reportRepository;
	}

	@Transactional
	public void cancel(Long reporterId, Long reportId) {
		Report report = reportRepository.findByIdAndReporterId(reportId, reporterId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		report.cancel();
	}
}
