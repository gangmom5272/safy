package com.ojimasam.safy.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.grid.domain.PlaceGridMapping;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.place.application.dto.CoordinateCongestionView;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

@ExtendWith(MockitoExtension.class)
class CoordinateCongestionReaderTest {
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");

	// 멀티캠퍼스 역삼지점 근처
	private static final BigDecimal LATITUDE = new BigDecimal("37.5013");
	private static final BigDecimal LONGITUDE = new BigDecimal("127.0396");
	private static final long CELL_ID = 900L;

	@Mock CityCongestionObservationRepository observationRepository;
	@Mock PlaceGridMappingRepository placeGridMappingRepository;
	@Mock GridCellRepository gridCellRepository;
	@Mock PlaceRepository placeRepository;

	/**
	 * 격자에 저장된 위험 등급이 아니라, 그 격자를 덮는 장소의 <b>서울시 원본 4단계</b>를
	 * 그대로 쓴다. 그래야 자치구·121장소 검색과 척도가 같아진다.
	 */
	@Test
	void usesTheOriginalFourLevelFromThePlaceCoveringTheGridCell() {
		givenGridCellExists();
		when(placeGridMappingRepository.findAll()).thenReturn(List.of(PlaceGridMapping.of(14L, CELL_ID)));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(14L, "역삼역", CongestionLevel.SLIGHTLY_CROWDED, 24000, NOW)
		));

		CoordinateCongestionView result = reader().at(LATITUDE, LONGITUDE).orElseThrow();

		assertThat(result.matchType()).isEqualTo(CoordinateCongestionView.MATCH_GRID);
		assertThat(result.congestion().placeName()).isEqualTo("역삼역");
		// '약간 붐빔'이 위험 등급 medium 으로 뭉개지지 않고 그대로 나온다.
		assertThat(result.congestion().congestionLevel()).isEqualTo(CongestionLevel.SLIGHTLY_CROWDED);
		assertThat(result.congestion().populationMax()).isEqualTo(24000);
	}

	/** 한 격자를 여러 장소가 덮으면 가장 혼잡한 곳을 쓴다(격자 명세 6.2와 같은 규칙). */
	@Test
	void picksTheMostCrowdedPlaceWhenSeveralCoverTheSameCell() {
		givenGridCellExists();
		when(placeGridMappingRepository.findAll()).thenReturn(List.of(
				PlaceGridMapping.of(14L, CELL_ID),
				PlaceGridMapping.of(1L, CELL_ID)
		));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(14L, "역삼역", CongestionLevel.NORMAL, 20000, NOW),
				observation(1L, "강남 MICE 관광특구", CongestionLevel.CROWDED, 15000, NOW)
		));

		CoordinateCongestionView result = reader().at(LATITUDE, LONGITUDE).orElseThrow();

		assertThat(result.congestion().placeName()).isEqualTo("강남 MICE 관광특구");
	}

	/** 격자를 덮는 장소가 없으면 최근접 장소로 대체하고, 그 사실을 matchType 으로 알린다. */
	@Test
	void fallsBackToNearestPlaceAndMarksMatchType() {
		when(gridCellRepository.findByGridCode(org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(Optional.empty());
		// 약 300m 떨어진 장소
		Place nearby = place(14L, "역삼역", "37.5040", "127.0396");
		when(placeRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(nearby));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(14L, "역삼역", CongestionLevel.NORMAL, 20000, NOW)
		));

		CoordinateCongestionView result = reader().at(LATITUDE, LONGITUDE).orElseThrow();

		assertThat(result.matchType()).isEqualTo(CoordinateCongestionView.MATCH_NEAREST);
		assertThat(result.congestion().placeName()).isEqualTo("역삼역");
	}

	/**
	 * 컷오프가 없으면 서울 외곽 좌표에 한참 떨어진 장소를 근거로 들이댄다. 그건 정보가
	 * 아니라 오해이므로 '정보 없음'으로 둔다.
	 */
	@Test
	void returnsEmptyWhenNearestPlaceIsBeyondTheCutoff() {
		when(gridCellRepository.findByGridCode(org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(Optional.empty());
		// 위도 0.1도 ≈ 11km
		Place faraway = place(14L, "역삼역", "37.6013", "127.0396");
		when(placeRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(faraway));
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(14L, "역삼역", CongestionLevel.NORMAL, 20000, NOW)
		));

		assertThat(reader().at(LATITUDE, LONGITUDE)).isEmpty();
	}

	/** 수집이 멈춘 관측은 어느 경로로도 쓰지 않는다(수집 시각 기준). */
	@Test
	void dropsObservationsCollectedOutsideTheFreshWindow() {
		when(observationRepository.findLatestPerPlace()).thenReturn(List.of(
				observation(14L, "역삼역", CongestionLevel.CROWDED, 50000,
						NOW.minus(CoordinateCongestionReader.FRESH_WINDOW).minusSeconds(1))
		));

		assertThat(reader().at(LATITUDE, LONGITUDE)).isEmpty();
	}

	@Test
	void returnsEmptyWhenCoordinateIsMissing() {
		assertThat(reader().at(null, LONGITUDE)).isEmpty();
		assertThat(reader().at(LATITUDE, null)).isEmpty();
	}

	private void givenGridCellExists() {
		GridCell cell = mock(GridCell.class);
		when(cell.getId()).thenReturn(CELL_ID);
		when(gridCellRepository.findByGridCode(org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(Optional.of(cell));
	}

	private CoordinateCongestionReader reader() {
		return new CoordinateCongestionReader(observationRepository, placeGridMappingRepository,
				gridCellRepository, placeRepository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Place place(Long id, String name, String latitude, String longitude) {
		Place place = Place.create("POI014", name, PlaceCategory.POPULATION_DENSE,
				new BigDecimal(latitude), new BigDecimal(longitude), "seoul-citydata");
		ReflectionTestUtils.setField(place, "id", id);
		return place;
	}

	private CityCongestionObservation observation(
			Long placeId, String placeName, CongestionLevel level, Integer populationMax, Instant collectedAt) {
		Place place = place(placeId, placeName, "37.5", "127.0");
		return CityCongestionObservation.create(place, null, "POI014", placeName, level,
				"안내 문구", populationMax / 2, populationMax, collectedAt, collectedAt, "seoul-citydata");
	}
}
