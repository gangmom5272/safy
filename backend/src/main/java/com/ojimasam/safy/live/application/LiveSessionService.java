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

@Service
public class LiveSessionService {
	private final LiveSessionRepository repository;
	private final LiveMediaGateway mediaGateway;
	private final ReportRepository reportRepository;
	private final OpenViduProperties properties;
	private final Clock clock;

	@Autowired
	public LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OpenViduProperties properties) {
		this(repository, mediaGateway, reportRepository, properties, Clock.systemUTC());
	}

	LiveSessionService(LiveSessionRepository repository, LiveMediaGateway mediaGateway,
			ReportRepository reportRepository, OpenViduProperties properties, Clock clock) {
		this.repository = repository;
		this.mediaGateway = mediaGateway;
		this.reportRepository = reportRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public LiveSessionView create(CreateLiveSessionCommand command) {
		Report report = reportRepository.save(Report.createLive(command.reporterId(), command.description(),
				command.latitude(), command.longitude(), command.address()));
		LiveSession session = LiveSession.create(report.getId(), command.reporterId(),
				clock.instant(), properties.sessionTtl());
		repository.save(session);
		try {
			mediaGateway.createRoom(session.getRoomName());
			return LiveSessionView.from(session);
		} catch (LiveMediaException exception) {
			session.markFailed(exception.getMessage(), clock.instant());
			throw new BusinessException(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE);
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
			session.activate(clock.instant());
			return token(session, "reporter-" + reporterId, LiveParticipantRole.REPORTER);
		} catch (LiveSessionAccessDeniedException exception) {
			throw new BusinessException(LiveSessionErrorCode.ACCESS_DENIED);
		} catch (IllegalStateException exception) {
			throw new BusinessException(LiveSessionErrorCode.NOT_JOINABLE, exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public LiveTokenView issueViewerToken(Long operatorId, UUID sessionId) {
		return readOnlyToken(find(sessionId), "operator-" + operatorId, LiveParticipantRole.VIEWER);
	}

	@Transactional(readOnly = true)
	public LiveTokenView issueAiToken(UUID sessionId) {
		return readOnlyToken(find(sessionId), "ai-" + sessionId, LiveParticipantRole.AI);
	}

	@Transactional(readOnly = true)
	public OperatorLiveSessionView getForOperator(UUID sessionId) {
		LiveSession session = find(sessionId);
		return operatorView(session);
	}

	@Transactional(readOnly = true)
	public LiveSessionCursorView getForOperator(LiveSessionStatus status, Instant cursor, int size) {
		List<LiveSessionStatus> statuses = status == null
				? Arrays.asList(LiveSessionStatus.CREATED, LiveSessionStatus.ACTIVE)
				: List.of(status);
		Instant before = cursor == null ? Instant.parse("9999-12-31T23:59:59.999999Z") : cursor;
		List<LiveSession> sessions = repository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
				statuses, before, PageRequest.of(0, size + 1));
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
