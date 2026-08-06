package com.ojimasam.safy.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojimasam.safy.notification.domain.NotificationPushDeliveryJob;
import com.ojimasam.safy.notification.domain.type.NotificationPushDeliveryStatus;

@ExtendWith(MockitoExtension.class)
class NotificationPushDeliveryCleanupWorkerTest {
	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

	@Mock JpaNotificationPushDeliveryJobRepository jobs;
	@Mock TransactionTemplate transactions;

	@BeforeEach
	void runCallbacksInline() {
		lenient().when(transactions.execute(any())).thenAnswer(invocation ->
				invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));
	}

	@Test
	void skipsCleanupWhenRetentionIsDisabled() {
		worker(Duration.ZERO).purgeFinishedJobs();

		verify(jobs, never()).findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(any(), any());
		verify(jobs, never()).deleteAllInBatch(anyList());
	}

	@Test
	void purgesOnlyJobsFinishedBeforeTheRetentionWindow() {
		when(jobs.findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(any(), any()))
				.thenReturn(List.of());
		ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);

		worker(Duration.ofDays(7)).purgeFinishedJobs();

		verify(jobs).findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(
				any(), threshold.capture());
		assertEquals(NOW.minus(Duration.ofDays(7)), threshold.getValue());
	}

	@Test
	void purgesOnlyCompletedAndFailedJobs() {
		when(jobs.findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(any(), any()))
				.thenReturn(List.of());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<NotificationPushDeliveryStatus>> statuses = ArgumentCaptor.forClass(List.class);

		worker(Duration.ofDays(7)).purgeFinishedJobs();

		verify(jobs).findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(
				statuses.capture(), any());
		assertEquals(List.of(NotificationPushDeliveryStatus.COMPLETED,
				NotificationPushDeliveryStatus.FAILED), statuses.getValue());
	}

	@Test
	void keepsPurgingWhileBatchesComeBackFull() {
		when(jobs.findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(any(), any()))
				.thenReturn(Collections.nCopies(500, job()), List.of(job()));

		worker(Duration.ofDays(7)).purgeFinishedJobs();

		verify(jobs, times(2)).deleteAllInBatch(anyList());
	}

	@Test
	void stopsAfterTheFirstPartialBatch() {
		when(jobs.findTop500ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(any(), any()))
				.thenReturn(Collections.nCopies(499, job()));

		worker(Duration.ofDays(7)).purgeFinishedJobs();

		verify(jobs, times(1)).deleteAllInBatch(anyList());
	}

	private NotificationPushDeliveryJob job() {
		return NotificationPushDeliveryJob.create(1L, 2L, NOW.minus(Duration.ofDays(30)));
	}

	private NotificationPushDeliveryCleanupWorker worker(Duration completedRetention) {
		WebPushProperties properties = new WebPushProperties(true, "mailto:test@example.com",
				"public", "private", 86400, Duration.ofSeconds(3), Duration.ofSeconds(5),
				Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofSeconds(30),
				Duration.ofMinutes(1), 8, 8, completedRetention, Map.of(),
				List.of("fcm.googleapis.com"));
		return new NotificationPushDeliveryCleanupWorker(jobs, properties, transactions,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
