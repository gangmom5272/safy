package com.ojimasam.safy.live.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionAccessDeniedException;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.application.OperatorReportAccessService;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

@Service
public class LiveSessionService {
	private final LiveSessionRepository repository;
	private final LiveMediaGateway mediaGateway;
	private final ReportRepository reportRepository;
	private final OperatorReportAccessService operatorAccessService;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final OpenViduProperties properties;
	private final Clock clock;

	@Autowired
	public LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OperatorReportAccessService operatorAccessService,
			ReportRealtimeNotifier realtimeNotifier, OpenViduProperties properties) {
		this(repository, mediaGateway, reportRepository, operatorAccessService, realtimeNotifier,
				properties, Clock.systemUTC());
	}

	LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OperatorReportAccessService operatorAccessService,
			ReportRealtimeNotifier realtimeNotifier, OpenViduProperties properties, Clock clock) {
		this.repository = repository;
		this.mediaGateway = mediaGateway;
		this.reportRepository = reportRepository;
		this.operatorAccessService = operatorAccessService;
		this.realtimeNotifier = realtimeNotifier;
		this.properties = properties;
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
		List<OperatorLiveSessionView> content = page.stream().map(this::operatorView).toList();
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
		try {
			mediaGateway.closeRoom(session.getRoomName());
			session.end(clock.instant());
		} catch (LiveMediaException exception) {
			session.markFailed(exception.getMessage(), clock.instant());
			throw new BusinessException(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE);
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
		Report report = reportRepository.findOneById(session.getReportId())
				.orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
		return OperatorLiveSessionView.from(session, report);
	}

	private LiveSession find(UUID id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
	}

	private LiveSession findForUpdate(UUID id) {
		return repository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(LiveSessionErrorCode.NOT_FOUND));
	}
}
