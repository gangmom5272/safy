package com.ojimasam.safy.notification.infrastructure;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.type.NotificationType;

public interface JpaNotificationRepository extends JpaRepository<Notification, Long> {
	Page<Notification> findByRecipientUserIdOrderByCreatedAtDescIdDesc(Long recipientUserId, Pageable pageable);

	Page<Notification> findByRecipientUserIdAndTypeOrderByCreatedAtDescIdDesc(
			Long recipientUserId, NotificationType type, Pageable pageable);

	Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

	boolean existsBySourceEventId(Long sourceEventId);

	long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

	@Modifying
	@Query("""
			update Notification notification
			set notification.readAt = :now
			where notification.recipientUserId = :recipientUserId and notification.readAt is null
			""")
	int markAllAsRead(@Param("recipientUserId") Long recipientUserId, @Param("now") Instant now);
}
