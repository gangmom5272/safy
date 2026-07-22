package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class CreateTextReportService {
	private final ReportRepository reportRepository;

	public CreateTextReportService(ReportRepository reportRepository) {
		this.reportRepository = reportRepository;
	}

	@Transactional
	public CreateReportResult create(CreateTextReportCommand command) {
		Report report = Report.createText(
				command.reporterId(),
				command.description(),
				command.latitude(),
				command.longitude(),
				command.address()
		);
		Report savedReport = reportRepository.save(report);
		return CreateReportResult.from(savedReport);
	}
}
