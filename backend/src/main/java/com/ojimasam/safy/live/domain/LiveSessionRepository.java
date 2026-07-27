package com.ojimasam.safy.live.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

public interface LiveSessionRepository {
	LiveSession save(LiveSession session);
	Optional<LiveSession> findById(UUID id);
	Optional<LiveSession> findByIdForUpdate(UUID id);
	Optional<LiveSession> findByReportId(Long reportId);
	Optional<LiveSession> findByRoomName(String roomName);
	List<LiveSession> findTop100ByStatusAndDisconnectedAtBeforeOrderByDisconnectedAtAsc(
			LiveSessionStatus status, Instant threshold);
	List<LiveSession> findAllByReportIdIn(Collection<Long> reportIds);
	List<LiveSession> findByStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
			Collection<LiveSessionStatus> statuses, Instant cursor, Pageable pageable);
	List<LiveSession> findAccessibleForOperator(
			Long operatorId,
			Collection<LiveSessionStatus> liveStatuses,
			Collection<com.ojimasam.safy.report.domain.ReportOperatorAssignmentStatus> assignmentStatuses,
			Instant cursor,
			Pageable pageable
	);
	List<LiveSession> findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
			Collection<LiveSessionStatus> statuses, Instant expiresAt);
}
