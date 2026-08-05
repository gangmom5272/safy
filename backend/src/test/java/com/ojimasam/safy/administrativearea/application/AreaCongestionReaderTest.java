package com.ojimasam.safy.administrativearea.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

@ExtendWith(MockitoExtension.class)
class AreaCongestionReaderTest {
	private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");
	private static final String GANGNAM = "1168000000";

	@Mock CityCongestionObservationRepository observationRepository;
	@Mock AreaPlaceIndex areaPlaceIndex;

	/** 구 안 인구를 합산하지 않는다. 가장 혼잡한 장소 하나를 그대로 보여준다. */
	@Test
	void picksTheMostCrowdedPlaceInsteadOfSumming() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of(1L, 2L));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(1L, "강남역", CongestionLevel.NORMAL, 20000, NOW),
				observation(2L, "압구정로데오거리", CongestionLevel.CROWDED, 12000, NOW)
		));

		var result = reader().peakOf(GANGNAM).orElseThrow();

		assertThat(result.placeName()).isEqualTo("압구정로데오거리");
		assertThat(result.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
		assertThat(result.populationMax()).isEqualTo(12000);
	}

	/** 같은 등급이면 추정 인원이 많은 장소가 그 구를 대표한다. */
	@Test
	void breaksLevelTiesByPopulation() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of(1L, 2L));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(1L, "강남역", CongestionLevel.CROWDED, 50000, NOW),
				observation(2L, "선릉역", CongestionLevel.CROWDED, 30000, NOW)
		));

		assertThat(reader().peakOf(GANGNAM).orElseThrow().placeName()).isEqualTo("강남역");
	}

	/** 다른 구의 장소가 섞여 들어오면 안 된다. */
	@Test
	void ignoresPlacesOutsideTheArea() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of(1L));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(1L, "강남역", CongestionLevel.NORMAL, 20000, NOW),
				observation(9L, "홍대입구역(2호선)", CongestionLevel.CROWDED, 40000, NOW)
		));

		assertThat(reader().peakOf(GANGNAM).orElseThrow().placeName()).isEqualTo("강남역");
	}

	/**
	 * 수집이 멈춰도 findLatestPerPlace 는 장소별 최신 1건을 계속 준다. 창 밖 관측을
	 * 실시간으로 보여주면 안 된다.
	 */
	@Test
	void dropsObservationsOlderThanTheFreshWindow() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of(1L));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(1L, "강남역", CongestionLevel.CROWDED, 50000,
						NOW.minus(AreaCongestionReader.FRESH_WINDOW).minusSeconds(1))
		));

		assertThat(reader().peakOf(GANGNAM)).isEmpty();
	}

	@Test
	void keepsObservationsInsideTheFreshWindow() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of(1L));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(1L, "강남역", CongestionLevel.CROWDED, 50000, NOW.minus(Duration.ofMinutes(14)))
		));

		assertThat(reader().peakOf(GANGNAM)).isPresent();
	}

	/** 121장소가 없는 자치구는 관측 조회 자체를 하지 않는다. */
	@Test
	void returnsEmptyWithoutQueryingWhenAreaHasNoPlace() {
		when(areaPlaceIndex.placeIdsOf(GANGNAM)).thenReturn(Set.of());

		assertThat(reader().peakOf(GANGNAM)).isEmpty();
	}

	private AreaCongestionReader reader() {
		return new AreaCongestionReader(observationRepository, areaPlaceIndex, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private CityCongestionObservation observation(
			Long placeId, String placeName, CongestionLevel level, Integer populationMax, Instant observedAt) {
		Place place = Place.create("POI001", placeName, PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.5"), new BigDecimal("127.0"), "seoul-citydata");
		ReflectionTestUtils.setField(place, "id", placeId);
		return CityCongestionObservation.create(place, null, "POI001", placeName, level,
				"안내 문구", populationMax == null ? null : populationMax / 2, populationMax,
				observedAt, observedAt, "seoul-citydata");
	}
}
