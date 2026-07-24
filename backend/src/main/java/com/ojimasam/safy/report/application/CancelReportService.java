package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistory;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;

@Service
public class CancelReportService {
	private final ReportRepository reportRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final Clock clock;

	@Autowired
	public CancelReportService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository
	) {
		this(reportRepository, routeRepository, assignmentRepository, historyRepository, Clock.systemUTC());
	}

	CancelReportService(
			ReportRepository reportRepository,
			ReportCenterRouteRepository routeRepository,
			ReportOperatorAssignmentRepository assignmentRepository,
			ReportStatusHistoryRepository historyRepository,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.routeRepository = routeRepository;
		this.assignmentRepository = assignmentRepository;
		this.historyRepository = historyRepository;
		this.clock = clock;
	}

	@Transactional
	public void cancel(Long reporterId, Long reportId) {
		Report report = reportRepository.findByIdAndReporterId(reportId, reporterId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		ReportStatus previous = report.getStatus();
		try {
			report.cancel();
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
		assignmentRepository.findByReport_IdAndStatusIn(reportId, Set.of(
				ReportOperatorAssignmentStatus.ASSIGNED,
				ReportOperatorAssignmentStatus.ACCEPTED,
				ReportOperatorAssignmentStatus.IN_PROGRESS
		)).ifPresent(assignment -> assignment.cancel(clock.instant()));
		routeRepository.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.ifPresent(route -> route.close(clock.instant()));
		historyRepository.save(ReportStatusHistory.record(
				report, previous, report.getStatus(), null, "REPORTER_CANCELLED", clock.instant()
		));
	}
}
