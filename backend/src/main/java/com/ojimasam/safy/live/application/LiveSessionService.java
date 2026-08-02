package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import com.ojimasam.safy.analysis.application.dto.RiskSummaryView;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionAccessDeniedException;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.application.OperatorReportAccessService;
import com.ojimasam.safy.report.application.ReportOperatorAssignmentService;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;
import com.ojimasam.safy.live.application.dto.CreateLiveSessionCommand;
import com.ojimasam.safy.live.application.dto.LiveSessionCursorView;
import com.ojimasam.safy.live.application.dto.LiveSessionView;
import com.ojimasam.safy.live.application.dto.LiveTokenView;
import com.ojimasam.safy.live.application.dto.OperatorLiveSessionView;

@Service
public class LiveSessionService {
	private final LiveSessionRepository repository;
	private final LiveMediaGateway mediaGateway;
	private final ReportRepository reportRepository;
	private final OperatorReportAccessService operatorAccessService;
	private final ReportOperatorAssignmentService assignmentService;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final LiveRecordingService recordingService;
	private final OpenViduProperties properties;
	private final RiskAssessmentRepository riskAssessmentRepository;
	private final Clock clock;

	@Autowired
	public LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OperatorReportAccessService operatorAccessService,
			ReportOperatorAssignmentService assignmentService,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			OpenViduProperties properties, RiskAssessmentRepository riskAssessmentRepository) {
		this(repository, mediaGateway, reportRepository, operatorAccessService, assignmentService, realtimeNotifier,
				recordingService, properties, riskAssessmentRepository, Clock.systemUTC());
	}

	LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OperatorReportAccessService operatorAccessService,
			ReportOperatorAssignmentService assignmentService,
			ReportRealtimeNotifier realtimeNotifier, LiveRecordingService recordingService,
			OpenViduProperties properties, RiskAssessmentRepository riskAssessmentRepository, Clock clock) {
		this.repository = repository;
		this.mediaGateway = mediaGateway;
		this.reportRepository = reportRepository;
		this.operatorAccessService = operatorAccessService;
		this.assignmentService = assignmentService;
		this.realtimeNotifier = realtimeNotifier;
		this.recordingService = recordingService;
		this.properties = properties;
		this.riskAssessmentRepository = riskAssessmentRepository;
		this.clock = clock;
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public LiveSessionView create(CreateLiveSessionCommand command) {
		Report report = reportRepository.findByIdForUpdate(command.reportId())
				.orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
		try {
			report.requireMediaCreation(command.reporterId(), ReportType.LIVE);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(LiveSessionErrorCode.ACCESS_DENIED);
		} catch (IllegalStateException exception) {
			throw new BusinessException(LiveSessionErrorCode.NOT_JOINABLE, exception.getMessage());
		}
		var existing = repository.findByReportId(report.getId());
		if (existing.filter(session -> session.getStatus() == LiveSessionStatus.CREATED
				|| session.getStatus() == LiveSessionStatus.ACTIVE).isPresent()) {
			return LiveSessionView.from(existing.orElseThrow());
		}
		LiveSession session = existing
				.map(failed -> retryCreation(failed, clock.instant()))
				.orElseGet(() -> repository.save(LiveSession.create(
						report.getId(), command.reporterId(), clock.instant(), properties.sessionTtl())));
		try {
			mediaGateway.createRoom(session.getRoomName());
			realtimeNotifier.liveReady(report.getId(), session.getId().toString());
			return LiveSessionView.from(session);
		} catch (LiveMediaException exception) {
			session.markFailed(exception.getMessage(), clock.instant());
			throw new BusinessException(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE);
		}
	}

	private LiveSession retryCreation(LiveSession session, Instant now) {
		try {
			session.retryCreation(now, properties.sessionTtl());
			return session;
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw new BusinessException(LiveSessionErrorCode.NOT_JOINABLE, exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public LiveSessionView getForReporter(Long reporterId, UUID sessionId) {
		LiveSession session = find(sessionId);
		try {
			session.requireOwnedBy(reporterId);
			return LiveSessionView.from(session);
		} catch (LiveSessionAccessDeniedException exception) {
			throw new BusinessException(LiveSessionErrorCode.ACCESS_DENIED);
		}
	}

	@Transactional
	public LiveTokenView issueReporterToken(Long reporterId, UUID sessionId) {
		LiveSession session = findForUpdate(sessionId);
		try {
			session.requireOwnedBy(reporterId);
			boolean activating = session.getStatus() == LiveSessionStatus.CREATED;
			session.activate(clock.instant());
			LiveTokenView token = token(session, "reporter-" + reporterId, LiveParticipantRole.REPORTER);
			if (activating) realtimeNotifier.liveActive(session.getReportId(), session.getId().toString());
			return token;
		} catch (LiveSessionAccessDeniedException exception) {
			throw new BusinessException(LiveSessionErrorCode.ACCESS_DENIED);
		} catch (IllegalStateException exception) {
			throw new BusinessException(LiveSessionErrorCode.NOT_JOINABLE, exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public LiveTokenView issueViewerToken(Long operatorId, UUID sessionId) {
		LiveSession session = find(sessionId);
		operatorAccessService.requireAccess(operatorId, session.getReportId());
		assignmentService.markHandling(operatorId, session.getReportId());
		return readOnlyToken(session, "operator-" + operatorId, LiveParticipantRole.VIEWER);
	}

	@Transactional(readOnly = true)
	public LiveTokenView issueAiToken(UUID sessionId) {
		return readOnlyToken(find(sessionId), "ai-" + sessionId, LiveParticipantRole.AI);
	}

	@Transactional(readOnly = true)
	public OperatorLiveSessionView getForOperator(Long operatorId, UUID sessionId) {
		LiveSession session = find(sessionId);
		operatorAccessService.requireAccess(operatorId, session.getReportId());
		return operatorView(session);
	}

	@Transactional(readOnly = true)
	public LiveSessionCursorView getForOperator(Long operatorId, LiveSessionStatus status, Instant cursor, int size) {
		List<LiveSessionStatus> statuses = status == null
				? Arrays.asList(LiveSessionStatus.CREATED, LiveSessionStatus.ACTIVE)
				: List.of(status);
		Instant before = cursor == null ? Instant.parse("9999-12-31T23:59:59.999999Z") : cursor;
		List<LiveSession> sessions = operatorAccessService.isSuperAdmin(operatorId)
				? repository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
						statuses, before, PageRequest.of(0, size + 1))
				: repository.findAccessibleForOperator(
						operatorId, statuses, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES,
						before, PageRequest.of(0, size + 1)
				);
		boolean hasNext = sessions.size() > size;
		List<LiveSession> page = hasNext ? sessions.subList(0, size) : sessions;
		Map<Long, RiskSummaryView> risks = risksByReportId(page.stream().map(LiveSession::getReportId).toList());
		List<OperatorLiveSessionView> content = page.stream()
				.map(session -> operatorView(session, risks.get(session.getReportId())))
				.toList();
		Instant nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getCreatedAt() : null;
		return new LiveSessionCursorView(content, nextCursor, hasNext);
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public void end(Long reporterId, UUID sessionId) {
		LiveSession session = findForUpdate(sessionId);
		try {
			session.requireOwnedBy(reporterId);
		} catch (LiveSessionAccessDeniedException exception) {
			throw new BusinessException(LiveSessionErrorCode.ACCESS_DENIED);
		}
		if (session.getStatus() == LiveSessionStatus.ENDED) return;
		recordingService.stopForSession(session.getId());
		try {
			mediaGateway.closeRoom(session.getRoomName());
			session.end(clock.instant());
			realtimeNotifier.liveEndedToOperator(session.getReportId());
		} catch (LiveMediaException exception) {
			session.markFailed(exception.getMessage(), clock.instant());
			throw new BusinessException(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE);
		}
	}

	/**
	 * 담당자 종료(complete/reject)로 제보가 종결됐을 때 진행 중인 라이브를 서버 주도로 종료하고
	 * 제보자에게 통지한다. 종료 이벤트 리스너에서 호출된다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public void endByReport(Long reportId) {
		LiveSession session = repository.findByReportId(reportId).orElse(null);
		if (session == null || session.getStatus() == LiveSessionStatus.ENDED
				|| session.getStatus() == LiveSessionStatus.FAILED) return;
		recordingService.stopForSession(session.getId());
		try {
			mediaGateway.closeRoom(session.getRoomName());
			session.end(clock.instant());
			realtimeNotifier.liveEndedToReporter(session.getReportId(), session.getReporterId());
		} catch (LiveMediaException exception) {
			session.markFailed(exception.getMessage(), clock.instant());
		}
	}

	private LiveTokenView readOnlyToken(LiveSession session, String identity, LiveParticipantRole role) {
		try {
			session.requireJoinable(clock.instant());
			return token(session, identity, role);
		} catch (IllegalStateException exception) {
			throw new BusinessException(LiveSessionErrorCode.NOT_JOINABLE, exception.getMessage());
		}
	}

	private LiveTokenView token(LiveSession session, String identity, LiveParticipantRole role) {
		try {
			Instant now = clock.instant();
			Duration remaining = Duration.between(now, session.getExpiresAt());
			Duration ttl = remaining.compareTo(properties.tokenTtl()) < 0 ? remaining : properties.tokenTtl();
			String token = mediaGateway.issueToken(session.getRoomName(), identity, role, ttl);
			Instant tokenExpiresAt = now.plus(ttl);
			return new LiveTokenView(session.getId(), mediaGateway.serverUrl(), session.getRoomName(), identity,
					role, token, tokenExpiresAt);
		} catch (LiveMediaException exception) {
			throw new BusinessException(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE);
		}
	}

	private OperatorLiveSessionView operatorView(LiveSession session) {
		RiskSummaryView risk = riskAssessmentRepository.findByReportId(session.getReportId())
				.map(RiskSummaryView::from).orElse(null);
		return operatorView(session, risk);
	}

	private OperatorLiveSessionView operatorView(LiveSession session, RiskSummaryView risk) {
		Report report = reportRepository.findOneById(session.getReportId())
				.orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
		return OperatorLiveSessionView.from(session, report, risk);
	}

	private Map<Long, RiskSummaryView> risksByReportId(List<Long> reportIds) {
		if (reportIds.isEmpty()) return Map.of();
		Map<Long, RiskSummaryView> result = new HashMap<>();
		riskAssessmentRepository.findAllByReportIdIn(reportIds)
				.forEach(assessment -> result.put(assessment.getReportId(), RiskSummaryView.from(assessment)));
		return result;
	}

	private LiveSession find(UUID id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
	}

	private LiveSession findForUpdate(UUID id) {
		return repository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
	}
}
