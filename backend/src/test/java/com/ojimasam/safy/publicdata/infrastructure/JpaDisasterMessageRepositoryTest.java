package com.ojimasam.safy.publicdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.DisasterMessage;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaDisasterMessageRepositoryTest {
	@Autowired
	private JpaDisasterMessageRepository disasterMessageRepository;

	@Autowired
	private JpaCollectionRunRepository collectionRunRepository;

	/** regions(JSON 배열)와 error_details(JSON 객체)가 왕복 저장/조회되어야 한다. */
	@Test
	void savesAndLoadsJsonColumns() {
		CollectionRun run = collectionRunRepository.saveAndFlush(
				CollectionRun.create(
						"run-2026-07-29T04:00:00Z",
						CollectionJobType.DISASTER_MESSAGE,
						"safetydata.go.kr",
						Instant.parse("2026-07-29T04:00:00Z"),
						Instant.parse("2026-07-29T04:00:03Z"),
						CollectionStatus.PARTIAL_SUCCESS,
						9,
						1,
						"UPSTREAM_TIMEOUT",
						"일부 페이지 조회가 시간 초과되었습니다.",
						"{\"failedPages\":[3]}"
				)
		);

		DisasterMessage saved = disasterMessageRepository.saveAndFlush(
				DisasterMessage.create(
						run,
						"SN-20260729-0001",
						"강남구 일대 인구 밀집으로 통행에 주의하십시오.",
						List.of("서울특별시 강남구", "서울특별시 서초구"),
						"인구밀집",
						"안전안내",
						Instant.parse("2026-07-29T04:01:00Z"),
						"safetydata.go.kr",
						Instant.parse("2026-07-29T04:02:00Z")
				)
		);

		DisasterMessage found = disasterMessageRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getRegions()).containsExactly("서울특별시 강남구", "서울특별시 서초구");
		assertThat(found.getCollectionRun().getErrorDetails()).contains("failedPages");
		assertThat(found.getSentAt()).isEqualTo(Instant.parse("2026-07-29T04:01:00Z"));
		assertThat(found.getCreatedAt()).isNotNull();
	}

	/** 같은 실행을 두 번 받아도 external_run_id 로 재사용 대상을 찾을 수 있어야 한다. */
	@Test
	void findsCollectionRunByExternalRunId() {
		collectionRunRepository.saveAndFlush(
				CollectionRun.create(
						"run-2026-07-29T04:05:00Z",
						CollectionJobType.CITY_CONGESTION,
						"seoul-citydata",
						Instant.parse("2026-07-29T04:05:00Z"),
						Instant.parse("2026-07-29T04:05:40Z"),
						CollectionStatus.SUCCESS,
						121,
						0,
						null,
						null,
						null
				)
		);

		assertThat(collectionRunRepository.findByExternalRunId("run-2026-07-29T04:05:00Z")).isPresent();
		assertThat(collectionRunRepository.findByExternalRunId("run-없음")).isEmpty();
	}

	@Test
	void blocksDuplicateExternalIdPerSource() {
		CollectionRun run = collectionRunRepository.saveAndFlush(
				CollectionRun.create(
						"run-2026-07-29T04:10:00Z",
						CollectionJobType.DISASTER_MESSAGE,
						"safetydata.go.kr",
						Instant.parse("2026-07-29T04:10:00Z"),
						null,
						CollectionStatus.SUCCESS,
						1,
						0,
						null,
						null,
						null
				)
		);
		disasterMessageRepository.saveAndFlush(
				DisasterMessage.create(
						run,
						"SN-20260729-0002",
						"호우 예보에 따라 하천 근처 통행을 자제하십시오.",
						List.of("서울특별시"),
						"호우",
						"안전안내",
						Instant.parse("2026-07-29T04:11:00Z"),
						"safetydata.go.kr",
						Instant.parse("2026-07-29T04:12:00Z")
				)
		);

		assertThat(
				disasterMessageRepository.existsBySourceAndExternalId("safetydata.go.kr", "SN-20260729-0002")
		).isTrue();
		assertThat(
				disasterMessageRepository.existsBySourceAndExternalId("safetydata.go.kr", "SN-20260729-9999")
		).isFalse();
	}
}
