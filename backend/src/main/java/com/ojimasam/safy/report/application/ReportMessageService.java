package com.ojimasam.safy.report.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.RealtimeEventType;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportMessage;
import com.ojimasam.safy.report.domain.ReportMessageRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.UserRole;

@Service
public class ReportMessageService {
	private final ReportRepository reportRepository;
	private final ReportMessageRepository messageRepository;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final UserRepository userRepository;
	private final OperatorReportAccessService operatorAccessService;
	private final RealtimeEventService eventService;
	private final Clock clock;

	@Autowired
	public ReportMessageService(ReportRepository reportRepository, ReportMessageRepository messageRepository,
			ReportOperatorAssignmentRepository assignmentRepository, UserRepository userRepository,
			OperatorReportAccessService operatorAccessService, RealtimeEventService eventService) {
		this(reportRepository, messageRepository, assignmentRepository, userRepository, operatorAccessService,
				eventService, Clock.systemUTC());
	}

	ReportMessageService(ReportRepository reportRepository, ReportMessageRepository messageRepository,
			ReportOperatorAssignmentRepository assignmentRepository, UserRepository userRepository,
			OperatorReportAccessService operatorAccessService, RealtimeEventService eventService, Clock clock) {
		this.reportRepository = reportRepository;
		this.messageRepository = messageRepository;
		this.assignmentRepository = assignmentRepository;
		this.userRepository = userRepository;
		this.operatorAccessService = operatorAccessService;
		this.eventService = eventService;
		this.clock = clock;
	}

	@Transactional
	public ReportMessageResponse sendFromCitizen(Long citizenId, Long reportId, String content) {
		Report report = reportRepository.findByIdAndReporterId(reportId, citizenId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		ReportMessage saved = save(report, citizenId, UserRole.CITIZEN, content);
		assignmentRepository.findByReport_IdAndStatusIn(
				reportId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES
		).ifPresent(assignment -> eventService.record(assignment.getOperator().getId(),
				RealtimeEventType.REPORT_MESSAGE, reportId, null, saved.getId()));
		return ReportMessageResponse.from(saved);
	}

	@Transactional
	public ReportMessageResponse sendFromOperator(Long operatorId, Long reportId, String content) {
		operatorAccessService.requireAccess(operatorId, reportId);
		Report report = reportRepository.findOneById(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		User sender = userRepository.findById(operatorId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.ACCESS_DENIED));
		ReportMessage saved = save(report, operatorId, sender.getRole(), content);
		eventService.record(report.getReporterId(), RealtimeEventType.REPORT_MESSAGE,
				reportId, null, saved.getId());
		return ReportMessageResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public Page<ReportMessageResponse> listForCitizen(Long citizenId, Long reportId, Pageable pageable) {
		reportRepository.findByIdAndReporterId(reportId, citizenId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return messageRepository.findAllByReport_IdOrderBySentAtAscIdAsc(reportId, pageable)
				.map(ReportMessageResponse::from);
	}

	@Transactional(readOnly = true)
	public Page<ReportMessageResponse> listForOperator(Long operatorId, Long reportId, Pageable pageable) {
		operatorAccessService.requireAccess(operatorId, reportId);
		return messageRepository.findAllByReport_IdOrderBySentAtAscIdAsc(reportId, pageable)
				.map(ReportMessageResponse::from);
	}

	private ReportMessage save(Report report, Long senderId, UserRole senderRole, String content) {
		try {
			return messageRepository.save(ReportMessage.create(
					report, senderId, senderRole, content, clock.instant()));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		}
	}
}
