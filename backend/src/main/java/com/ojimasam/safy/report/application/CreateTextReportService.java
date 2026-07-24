package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class CreateTextReportService {
	private final ReportRepository reportRepository;
	private final ApplicationEventPublisher eventPublisher;

	public CreateTextReportService(ReportRepository reportRepository, ApplicationEventPublisher eventPublisher) {
		this.reportRepository = reportRepository;
		this.eventPublisher = eventPublisher;
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
		eventPublisher.publishEvent(new ReportRoutingRequestedEvent(savedReport.getId()));
		return CreateReportResult.from(savedReport);
	}
}
