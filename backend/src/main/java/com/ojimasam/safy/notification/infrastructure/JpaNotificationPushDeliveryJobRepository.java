package com.ojimasam.safy.notification.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;

import jakarta.persistence.LockModeType;

public interface JpaNotificationPushDeliveryJobRepository
		extends JpaRepository<NotificationPushDeliveryJob, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<NotificationPushDeliveryJob> findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
			Collection<NotificationPushDeliveryStatus> statuses, Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from NotificationPushDeliveryJob job where job.id = :id")
	Optional<NotificationPushDeliveryJob> findByIdForUpdate(@Param("id") Long id);

	List<NotificationPushDeliveryJob> findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
			NotificationPushDeliveryStatus status, Instant startedBefore);
}
