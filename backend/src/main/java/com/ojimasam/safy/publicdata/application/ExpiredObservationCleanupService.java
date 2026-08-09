package com.ojimasam.safy.publicdata.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

/**
 * 보관 기간을 넘긴 인구 밀집 관측값을 지운다.
 *
 * 지역 121곳 × 5분이라 월 100만 행씩 늘어나는데, 지도는 지역별 최신 1건만 쓰고
 * 위험도 추이 계산도 몇 주 분량이면 충분하므로 오래된 행은 보관 가치가 없다.
 */
@Service
public class ExpiredObservationCleanupService {
	private static final Logger log = LoggerFactory.getLogger(ExpiredObservationCleanupService.class);

	private final CityCongestionObservationRepository observationRepository;
	private final Duration retention;
	private final Clock clock;

	@Autowired
	public ExpiredObservationCleanupService(
			CityCongestionObservationRepository observationRepository,
			@Value("${app.public-data.observation-retention:P90D}") Duration retention
	) {
		this(observationRepository, retention, Clock.systemUTC());
	}

	ExpiredObservationCleanupService(
			CityCongestionObservationRepository observationRepository,
			Duration retention,
			Clock clock
	) {
		this.observationRepository = observationRepository;
		this.retention = retention;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.public-data.observation-cleanup-cron:0 30 4 * * *}")
	@Transactional
	public void cleanup() {
		Instant threshold = clock.instant().minus(retention);
		int deleted = observationRepository.deleteObservedBefore(threshold);
		if (deleted > 0) {
			log.info("보관 기간이 지난 인구 밀집 관측값 {}건을 삭제했습니다. threshold={}", deleted, threshold);
		}
	}
}
