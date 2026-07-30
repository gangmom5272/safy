package com.ojimasam.safy.publicdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.place.infrastructure.JpaPlaceRepository;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.CulturalEvent;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaCityCongestionObservationRepositoryTest {
	@Autowired
	private JpaCityCongestionObservationRepository observationRepository;

	@Autowired
	private JpaCulturalEventRepository culturalEventRepository;

	@Autowired
	private JpaCollectionRunRepository collectionRunRepository;

	@Autowired
	private JpaPlaceRepository placeRepository;

	@Test
	void savesAndLoadsObservation() {
		Place place = savePlace("POI001", "강남 MICE 관광특구", PlaceCategory.TOURIST_ZONE);
		CollectionRun run = saveRun("run-congestion-1");

		CityCongestionObservation saved = observationRepository.saveAndFlush(
				CityCongestionObservation.create(
						place,
						run,
						"POI001",
						"강남 MICE 관광특구",
						CongestionLevel.SLIGHTLY_CROWDED,
						"사람들이 몰려있을 가능성이 있어요.",
						12000,
						14000,
						Instant.parse("2026-07-29T04:00:00Z"),
						Instant.parse("2026-07-29T04:01:00Z"),
						"seoul-citydata"
				)
		);

		CityCongestionObservation found = observationRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getCongestionLevel()).isEqualTo(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(found.getPopulationMax()).isEqualTo(14000);
		assertThat(found.getPlace().getExternalPlaceId()).isEqualTo("POI001");
		assertThat(found.getCreatedAt()).isNotNull();
	}

	/** 같은 지역의 같은 관측 시각은 재폴링으로 들어와도 한 번만 저장되어야 한다. */
	@Test
	void rejectsDuplicatePlaceAndObservedAt() {
		Place place = savePlace("POI009", "광화문·덕수궁", PlaceCategory.HERITAGE);
		CollectionRun run = saveRun("run-congestion-2");
		Instant observedAt = Instant.parse("2026-07-29T04:05:00Z");

		observationRepository.saveAndFlush(observation(place, run, "POI009", observedAt));

		assertThat(observationRepository.existsByPlace_IdAndObservedAt(place.getId(), observedAt)).isTrue();
		assertThatThrownBy(() ->
				observationRepository.saveAndFlush(observation(place, run, "POI009", observedAt))
		).isInstanceOf(Exception.class);
	}

	@Test
	void savesAndUpdatesCulturalEventByExternalId() {
		Place place = savePlace("POI072", "여의도", PlaceCategory.COMMERCIAL);
		CollectionRun run = saveRun("run-event-1");

		culturalEventRepository.saveAndFlush(
				CulturalEvent.create(
						place,
						run,
						"seoul-citydata:POI072:여의도 봄꽃축제:2026-07-01~2026-08-30",
						"여의도 봄꽃축제",
						"윤중로 일대 봄꽃축제입니다.",
						"여의서로",
						"2026-07-01~2026-08-30",
						LocalDate.of(2026, 7, 1),
						LocalDate.of(2026, 8, 30),
						new BigDecimal("37.5285000"),
						new BigDecimal("126.9325000"),
						"POI072",
						"여의도",
						false,
						"https://example.test/thumb.jpg",
						"https://example.test/detail",
						"seoul-citydata",
						Instant.parse("2026-07-29T04:00:00Z")
				)
		);

		CulturalEvent found = culturalEventRepository
				.findBySourceAndExternalId(
						"seoul-citydata",
						"seoul-citydata:POI072:여의도 봄꽃축제:2026-07-01~2026-08-30"
				)
				.orElseThrow();

		assertThat(found.getTitle()).isEqualTo("여의도 봄꽃축제");
		assertThat(found.getPaid()).isFalse();
		assertThat(
				culturalEventRepository
						.findAllByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
								LocalDate.of(2026, 7, 29),
								LocalDate.of(2026, 7, 29)
						)
		).hasSize(1);
	}

	private CityCongestionObservation observation(
			Place place,
			CollectionRun run,
			String areaCode,
			Instant observedAt
	) {
		return CityCongestionObservation.create(
				place,
				run,
				areaCode,
				place.getName(),
				CongestionLevel.NORMAL,
				null,
				100,
				200,
				observedAt,
				observedAt,
				"seoul-citydata"
		);
	}

	private Place savePlace(String externalPlaceId, String name, PlaceCategory category) {
		return placeRepository.saveAndFlush(
				Place.create(
						externalPlaceId,
						name,
						category,
						new BigDecimal("37.5000000"),
						new BigDecimal("127.0000000"),
						"seoul-citydata"
				)
		);
	}

	private CollectionRun saveRun(String externalRunId) {
		return collectionRunRepository.saveAndFlush(
				CollectionRun.create(
						externalRunId,
						CollectionJobType.CITY_CONGESTION,
						"seoul-citydata",
						Instant.parse("2026-07-29T04:00:00Z"),
						Instant.parse("2026-07-29T04:00:30Z"),
						CollectionStatus.SUCCESS,
						121,
						0,
						null,
						null,
						null
				)
		);
	}
}
