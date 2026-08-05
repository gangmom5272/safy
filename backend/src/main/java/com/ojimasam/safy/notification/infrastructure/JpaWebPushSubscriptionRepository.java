package com.ojimasam.safy.notification.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.domain.type.WebPushSubscriptionStatus;

public interface JpaWebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {
	Optional<WebPushSubscription> findByEndpointHash(String endpointHash);

	Optional<WebPushSubscription> findByRecipientUserIdAndEndpointHash(Long recipientUserId, String endpointHash);

	List<WebPushSubscription> findAllByRecipientUserIdAndStatus(
			Long recipientUserId, WebPushSubscriptionStatus status);
}
