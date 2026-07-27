package com.ojimasam.safy.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ojimasam.safy.live.infrastructure.JpaLiveSessionRepository;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.report.infrastructure.JpaReportRepository;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiveSessionConcurrencyIntegrationTest {
	@Autowired LiveSessionService service;
	@Autowired JpaLiveSessionRepository liveSessionRepository;
	@Autowired JpaReportRepository reportRepository;
	@Autowired ReportRepository reports;
	@MockitoBean LiveMediaGateway mediaGateway;

	@BeforeEach
	void cleanUp() {
		liveSessionRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void concurrentCreationForSameReportReturnsOneSession() throws Exception {
		Report report = liveReport();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<LiveSessionView>> futures = List.of(
					executor.submit(() -> createAfterSignal(report.getId(), ready, start)),
					executor.submit(() -> createAfterSignal(report.getId(), ready, start)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			LiveSessionView first = futures.get(0).get(10, TimeUnit.SECONDS);
			LiveSessionView second = futures.get(1).get(10, TimeUnit.SECONDS);

			assertThat(first.streamRoomId()).isEqualTo(second.streamRoomId());
			assertThat(liveSessionRepository.count()).isEqualTo(1);
			verify(mediaGateway).createRoom("report-" + first.streamRoomId());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void failedRoomCreationIsPersistedAndRetriedWithSameSession() {
		Report report = liveReport();
		doThrow(new LiveMediaException("temporary failure"))
				.doNothing()
				.when(mediaGateway).createRoom(anyString());

		assertThatThrownBy(() -> service.create(new CreateLiveSessionCommand(1L, report.getId())))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE));
		LiveSession failed = liveSessionRepository.findByReportId(report.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(LiveSessionStatus.FAILED);

		LiveSessionView retried = service.create(new CreateLiveSessionCommand(1L, report.getId()));

		assertThat(retried.streamRoomId()).isEqualTo(failed.getId());
		assertThat(liveSessionRepository.findByReportId(report.getId()).orElseThrow().getStatus())
				.isEqualTo(LiveSessionStatus.CREATED);
	}

	@Test
	void reporterTokenFailureRollsBackSessionActivation() {
		Report report = liveReport();
		LiveSessionView created = service.create(new CreateLiveSessionCommand(1L, report.getId()));
		when(mediaGateway.issueToken(anyString(), anyString(), any(), any()))
				.thenThrow(new LiveMediaException("token failure"));

		assertThatThrownBy(() -> service.issueReporterToken(1L, created.streamRoomId()))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(LiveSessionErrorCode.MEDIA_SERVER_UNAVAILABLE));

		assertThat(liveSessionRepository.findByReportId(report.getId()).orElseThrow().getStatus())
				.isEqualTo(LiveSessionStatus.CREATED);
	}

	private LiveSessionView createAfterSignal(Long reportId, CountDownLatch ready, CountDownLatch start)
			throws InterruptedException {
		ready.countDown();
		start.await();
		return service.create(new CreateLiveSessionCommand(1L, reportId));
	}

	private Report liveReport() {
		return reports.save(Report.create(
				1L, ReportType.LIVE, ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.CROWD_INCREASING, "인파 증가 라이브",
				new BigDecimal("37.4979000"), new BigDecimal("127.0276000"), "서울 강남구"));
	}
}
