package com.ojimasam.safy.notification.infrastructure;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojimasam.safy.notification.domain.Notification;
import com.ojimasam.safy.notification.domain.WebPushSubscription;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;

@Component
public class WebPushSender {
	private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

	private final WebPushProperties properties;
	private final WebPushSubscriptionValidator subscriptionValidator;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private CloseableHttpClient httpClient;
	private PushService pushService;

	public WebPushSender(WebPushProperties properties, WebPushSubscriptionValidator subscriptionValidator) {
		this.properties = properties;
		this.subscriptionValidator = subscriptionValidator;
	}

	@PostConstruct
	void initialize() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(milliseconds(properties.connectTimeout().toMillis()))
				.setConnectionRequestTimeout(milliseconds(properties.connectTimeout().toMillis()))
				.setSocketTimeout(milliseconds(properties.readTimeout().toMillis()))
				.build();
		httpClient = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.disableRedirectHandling()
				.build();
		if (isConfigured()) {
			try {
				pushService = new PushService(
						properties.publicKey(), properties.privateKey(), properties.subject());
			} catch (GeneralSecurityException exception) {
				throw new IllegalStateException("Web Push VAPID 키가 올바르지 않습니다.", exception);
			}
		}
	}

	@PreDestroy
	void closeHttpClient() throws IOException {
		if (httpClient != null) httpClient.close();
	}

	public WebPushSendResult send(Notification notification, WebPushSubscription subscription) {
		if (!isConfigured()) return WebPushSendResult.FAILED;

		try {
			String endpoint = subscriptionValidator.validateEndpoint(subscription.getEndpoint());
			subscriptionValidator.validateKeys(subscription.getP256dh(), subscription.getAuth());
			byte[] payload = objectMapper.writeValueAsBytes(Map.of(
					"notificationId", notification.getId(),
					"title", notification.getTitle(),
					"body", notification.getContent(),
					"linkUrl", linkUrl(notification)
			));
			nl.martijndwars.webpush.Notification message = new nl.martijndwars.webpush.Notification(
					endpoint, subscription.getP256dh().trim(), subscription.getAuth().trim(), payload, ttlSeconds());
			HttpPost request = pushService.preparePost(message, Encoding.AES128GCM);
			try (CloseableHttpResponse response = httpClient.execute(request)) {
				int status = response.getStatusLine().getStatusCode();
				EntityUtils.consumeQuietly(response.getEntity());
				if (status >= 200 && status < 300) return WebPushSendResult.SUCCESS;
				if (status == 404 || status == 410) return WebPushSendResult.EXPIRED;
				log.warn("Web Push 전송이 거절되었습니다. status={}, notificationId={}, subscriptionId={}",
						status, notification.getId(), subscription.getId());
			}
		} catch (JsonProcessingException exception) {
			log.error("Web Push payload 직렬화에 실패했습니다. notificationId={}", notification.getId(), exception);
		} catch (GeneralSecurityException | IOException | JoseException exception) {
			log.warn("Web Push 전송에 실패했습니다. notificationId={}, subscriptionId={}",
					notification.getId(), subscription.getId(), exception);
		} catch (IllegalArgumentException exception) {
			log.warn("Web Push 구독 정보가 올바르지 않습니다. subscriptionId={}", subscription.getId(), exception);
		}
		return WebPushSendResult.FAILED;
	}

	public boolean isConfigured() {
		return properties.enabled() && notBlank(properties.publicKey())
				&& notBlank(properties.privateKey()) && notBlank(properties.subject());
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private int ttlSeconds() {
		long ttl = properties.ttlSeconds();
		return ttl > 0 && ttl <= Integer.MAX_VALUE ? (int) ttl : 86400;
	}

	private String linkUrl(Notification notification) {
		if (notification.getReportId() != null) {
			return "/app/mypage/reports/" + notification.getReportId();
		}
		if (notification.getAreaReportRequestId() != null) {
			return "/app/report?requestId=" + notification.getAreaReportRequestId();
		}
		return "/app/notifications";
	}

	private int milliseconds(long value) {
		return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(value, 1);
	}
}
