package com.ojimasam.safy.report.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class CreateReportService {
	private final ReportRepository reportRepository;
	private final ApplicationEventPublisher eventPublisher;

	public CreateReportService(ReportRepository reportRepository, ApplicationEventPublisher eventPublisher) {
		this.reportRepository = reportRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public CreateReportResult create(CreateReportCommand command) {
		// 주소는 라우팅 단계에서 서버가 좌표로 확정해 채운다.
		Report report = reportRepository.save(Report.create(
				command.reporterId(), command.type(), command.category(), command.subcategory(), command.description(),
				command.latitude(), command.longitude(), null
		));
		eventPublisher.publishEvent(new ReportRoutingRequestedEvent(report.getId()));
		return CreateReportResult.from(report);
	}
}
