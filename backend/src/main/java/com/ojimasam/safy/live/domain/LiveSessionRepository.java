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
	List<LiveSession> findByStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
			Collection<LiveSessionStatus> statuses, Instant cursor, Pageable pageable);
	List<LiveSession> findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
			Collection<LiveSessionStatus> statuses, Instant expiresAt);
}
