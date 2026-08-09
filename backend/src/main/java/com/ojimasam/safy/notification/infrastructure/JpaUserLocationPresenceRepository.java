package com.ojimasam.safy.notification.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.notification.domain.UserLocationPresence;

public interface JpaUserLocationPresenceRepository extends JpaRepository<UserLocationPresence, Long> {
	List<UserLocationPresence> findAllByPlaceIdAndLastLocationAtGreaterThanEqualAndLastSeenAtGreaterThanEqual(
			Long placeId, Instant locationSince, Instant seenSince);

	long deleteByLastSeenAtBefore(Instant cutoff);
}
