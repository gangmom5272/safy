package com.ojimasam.safy.publicdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
import com.ojimasam.safy.publicdata.application.dto.PlaceCongestionResponse;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class LatestCongestionQueryTest {
	@Autowired
	private JpaCityCongestionObservationRepository observationRepository;

	@Autowired
	private JpaCollectionRunRepository collectionRunRepository;

	@Autowired
	private JpaPlaceRepository placeRepository;

	@Test
	void returnsOnlyLatestObservationPerPlace() {
		CollectionRun run = saveRun();
		Place gangnam = savePlace("POI001", "강남 MICE 관광특구", PlaceCategory.TOURIST_ZONE);
		Place gwanghwamun = savePlace("POI009", "광화문·덕수궁", PlaceCategory.HERITAGE);

		save(gangnam, run, CongestionLevel.NORMAL, "2026-07-29T03:00:00Z", 1000);
		save(gangnam, run, CongestionLevel.CROWDED, "2026-07-29T04:00:00Z", 9000);
		save(gangnam, run, CongestionLevel.RELAXED, "2026-07-29T02:00:00Z", 500);
		save(gwanghwamun, run, CongestionLevel.SLIGHTLY_CROWDED, "2026-07-29T03:30:00Z", 4000);

		List<PlaceCongestionResponse> latest = observationRepository.findLatestPerPlace()
				.stream()
				.map(PlaceCongestionResponse::from)
				.toList();

		assertThat(latest).hasSize(2);

		PlaceCongestionResponse gangnamLatest = latest.stream()
				.filter(row -> row.externalPlaceId().equals("POI001"))
				.findFirst()
				.orElseThrow();
		assertThat(gangnamLatest.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
		assertThat(gangnamLatest.populationMax()).isEqualTo(9000);
		assertThat(gangnamLatest.observedAt()).isEqualTo(Instant.parse("2026-07-29T04:00:00Z"));
		assertThat(gangnamLatest.placeName()).isEqualTo("강남 MICE 관광특구");
	}

	/** 관측값이 아직 없는 지역은 지도 응답에 나타나지 않는다. */
	@Test
	void omitsPlaceWithoutObservation() {
		savePlace("POI072", "여의도", PlaceCategory.COMMERCIAL);

		assertThat(observationRepository.findLatestPerPlace()).isEmpty();
	}

	/**
	 * 좌표가 아직 없는 지역도 목록과 혼잡도는 유효하므로 응답에서 빠지지 않아야 한다.
	 * 서울시 121장소 목록에 좌표가 없어 지오코딩으로 나중에 채우는 동안에도 수집·조회가 동작해야 한다.
	 */
	@Test
	void keepsPlaceWithoutCoordinates() {
		CollectionRun run = saveRun();
		Place place = savePlace("POI130", "시의회 앞", PlaceCategory.POPULATION_DENSE, null, null);
		save(place, run, CongestionLevel.CROWDED, "2026-07-29T04:00:00Z", 9000);

		PlaceCongestionResponse latest = observationRepository.findLatestPerPlace()
				.stream()
				.map(PlaceCongestionResponse::from)
				.findFirst()
				.orElseThrow();

		assertThat(latest.placeName()).isEqualTo("시의회 앞");
		assertThat(latest.category()).isEqualTo(PlaceCategory.POPULATION_DENSE);
		assertThat(latest.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
		assertThat(latest.latitude()).isNull();
		assertThat(latest.longitude()).isNull();
	}

	/** 보관 기간을 넘긴 행만 지워지고 최신 행은 남아야 한다. */
	@Test
	void deletesOnlyObservationsBeforeThreshold() {
		CollectionRun run = saveRun();
		Place place = savePlace("POI005", "잠실 관광특구", PlaceCategory.TOURIST_ZONE);
		save(place, run, CongestionLevel.NORMAL, "2026-04-01T00:00:00Z", 1000);
		save(place, run, CongestionLevel.NORMAL, "2026-04-30T00:00:00Z", 1000);
		save(place, run, CongestionLevel.CROWDED, "2026-07-29T04:00:00Z", 9000);

		int deleted = observationRepository.deleteObservedBefore(Instant.parse("2026-05-01T00:00:00Z"));

		assertThat(deleted).isEqualTo(2);
		assertThat(observationRepository.count()).isEqualTo(1);
		assertThat(observationRepository.findAll().getFirst().getObservedAt())
				.isEqualTo(Instant.parse("2026-07-29T04:00:00Z"));
	}

	private void save(Place place, CollectionRun run, CongestionLevel level, String observedAt, int populationMax) {
		observationRepository.saveAndFlush(
				CityCongestionObservation.create(
						place,
						run,
						place.getExternalPlaceId(),
						place.getName(),
						level,
						null,
						populationMax - 500,
						populationMax,
						Instant.parse(observedAt),
						Instant.parse(observedAt),
						"seoul-citydata"
				)
		);
	}

	private Place savePlace(String externalPlaceId, String name, PlaceCategory category) {
		return savePlace(
				externalPlaceId,
				name,
				category,
				new BigDecimal("37.5000000"),
				new BigDecimal("127.0000000")
		);
	}

	private Place savePlace(
			String externalPlaceId,
			String name,
			PlaceCategory category,
			BigDecimal latitude,
			BigDecimal longitude
	) {
		return placeRepository.saveAndFlush(
				Place.create(externalPlaceId, name, category, latitude, longitude, "seoul-citydata")
		);
	}

	private CollectionRun saveRun() {
		return collectionRunRepository.saveAndFlush(
				CollectionRun.create(
						"run-latest-1",
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
