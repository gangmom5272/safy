package com.ojimasam.safy.admin.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

public interface OperatorPresenceRepository {
	OperatorPresence save(OperatorPresence presence);

	Optional<OperatorPresence> findByOperatorId(Long operatorId);

	List<OperatorPresence> findAllByOperatorIdIn(Collection<Long> operatorIds);

	List<OperatorPresence> findAllByStatusNot(OperatorPresenceStatus status);

	List<OperatorPresence> findAllByStatusAndLastSeenAtBefore(OperatorPresenceStatus status, Instant cutoff);
}
