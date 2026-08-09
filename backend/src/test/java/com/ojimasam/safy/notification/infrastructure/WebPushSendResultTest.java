package com.ojimasam.safy.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebPushSendResultTest {
	@Test
	void treatsAcceptedResponsesAsSuccess() {
		assertEquals(WebPushSendResult.SUCCESS, WebPushSendResult.fromHttpStatus(200));
		assertEquals(WebPushSendResult.SUCCESS, WebPushSendResult.fromHttpStatus(201));
	}

	@Test
	void treatsGoneSubscriptionAsExpired() {
		assertEquals(WebPushSendResult.EXPIRED, WebPushSendResult.fromHttpStatus(404));
		assertEquals(WebPushSendResult.EXPIRED, WebPushSendResult.fromHttpStatus(410));
	}

	@Test
	void doesNotRetryClientErrorsThatCannotSucceed() {
		assertEquals(WebPushSendResult.REJECTED, WebPushSendResult.fromHttpStatus(400));
		assertEquals(WebPushSendResult.REJECTED, WebPushSendResult.fromHttpStatus(403));
		assertEquals(WebPushSendResult.REJECTED, WebPushSendResult.fromHttpStatus(413));
	}

	@Test
	void retriesRateLimitAndServerErrors() {
		assertEquals(WebPushSendResult.FAILED, WebPushSendResult.fromHttpStatus(429));
		assertEquals(WebPushSendResult.FAILED, WebPushSendResult.fromHttpStatus(500));
		assertEquals(WebPushSendResult.FAILED, WebPushSendResult.fromHttpStatus(503));
	}
}
