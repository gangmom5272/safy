package com.ojimasam.safy.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.notification.domain.WebPushSubscription;
import com.ojimasam.safy.notification.infrastructure.JpaWebPushSubscriptionRepository;
import com.ojimasam.safy.notification.infrastructure.WebPushEndpointHasher;
import com.ojimasam.safy.notification.infrastructure.WebPushProperties;
import com.ojimasam.safy.notification.infrastructure.WebPushSubscriptionValidator;

@Service
public class WebPushSubscriptionService {
	private final JpaWebPushSubscriptionRepository repository;
	private final WebPushEndpointHasher endpointHasher;
	private final WebPushProperties properties;
	private final WebPushSubscriptionValidator validator;

	public WebPushSubscriptionService(JpaWebPushSubscriptionRepository repository,
			WebPushEndpointHasher endpointHasher, WebPushProperties properties,
			WebPushSubscriptionValidator validator) {
		this.repository = repository;
		this.endpointHasher = endpointHasher;
		this.properties = properties;
		this.validator = validator;
	}

	public String publicKey() {
		if (!properties.enabled() || blank(properties.publicKey()) || blank(properties.privateKey())
				|| blank(properties.subject())) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "Web Push 서버가 활성화되지 않았습니다.");
		}
		return properties.publicKey();
	}

	@Transactional
	public void register(Long recipientUserId, String endpoint, String p256dh, String auth, String userAgent) {
		String normalizedEndpoint;
		try {
			normalizedEndpoint = validator.validateEndpoint(endpoint);
			validator.validateKeys(p256dh, auth);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		}
		String endpointHash = endpointHasher.hash(normalizedEndpoint);
		WebPushSubscription subscription = repository.findByEndpointHash(endpointHash)
				.orElseGet(() -> WebPushSubscription.create(
						recipientUserId, normalizedEndpoint, endpointHash, p256dh.trim(), auth.trim(), trim(userAgent)));
		if (subscription.getId() != null) {
			subscription.refresh(recipientUserId, normalizedEndpoint, p256dh.trim(), auth.trim(), trim(userAgent));
		}
		repository.save(subscription);
	}

	@Transactional
	public void disable(Long recipientUserId, String endpoint) {
		String endpointHash = endpointHasher.hash(endpoint.trim());
		repository.findByRecipientUserIdAndEndpointHash(recipientUserId, endpointHash)
				.ifPresent(WebPushSubscription::disable);
	}

	private String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
