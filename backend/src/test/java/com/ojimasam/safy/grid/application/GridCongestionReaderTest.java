package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.grid.domain.PlaceGridMapping;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

class GridCongestionReaderTest {
	private static final Instant WINDOW_END = Instant.parse("2026-08-03T12:00:00Z");
	private static final Instant WINDOW_START = WINDOW_END.minus(Duration.ofMinutes(15));
	/** 창 안에 들어오는 관측 시각. */
	private static final Instant OBSERVED_AT = WINDOW_START.plus(Duration.ofMinutes(3));

	private final CityCongestionObservationRepository observations =
			mock(CityCongestionObservationRepository.class);
	private final PlaceGridMappingRepository mappings = mock(PlaceGridMappingRepository.class);
	private final GridCongestionReader reader = new GridCongestionReader(observations, mappings);

	@Test
	@DisplayName("장소의 혼잡도를 그 장소가 덮는 셀 전부에 붙인다")
	void spreadsPlaceCongestionAcrossItsCells() {
		givenObservations(observation(1L, CongestionLevel.CROWDED));
		givenMappings(mapping(1L, 100L), mapping(1L, 101L), mapping(1L, 102L));

		Map<Long, CongestionLevel> byCell = reader.latestByGridCell(WINDOW_START);

		assertThat(byCell).containsOnlyKeys(100L, 101L, 102L);
		assertThat(byCell.values()).containsOnly(CongestionLevel.CROWDED);
	}

	@Test
	@DisplayName("한 셀에 여러 장소가 걸치면 가장 높은 혼잡 등급을 쓴다")
	void keepsHighestCongestionWhenPlacesOverlap() {
		// 홍대입구역이 홍대 관광특구 안에 있는 것과 같은 구조다.
		givenObservations(
				observation(1L, CongestionLevel.RELAXED),
				observation(2L, CongestionLevel.SLIGHTLY_CROWDED),
				observation(3L, CongestionLevel.NORMAL)
		);
		givenMappings(mapping(1L, 100L), mapping(2L, 100L), mapping(3L, 100L));

		assertThat(reader.latestByGridCell(WINDOW_START)).containsEntry(100L, CongestionLevel.SLIGHTLY_CROWDED);
	}

	@Test
	@DisplayName("등급 비교는 선언 순서를 따른다")
	void ordersLevelsByDeclaration() {
		// 문자열로 비교하면 CROWDED 가 RELAXED 보다 작아져 결과가 뒤집힌다.
		givenObservations(
				observation(1L, CongestionLevel.CROWDED),
				observation(2L, CongestionLevel.RELAXED)
		);
		givenMappings(mapping(1L, 100L), mapping(2L, 100L));

		assertThat(reader.latestByGridCell(WINDOW_START)).containsEntry(100L, CongestionLevel.CROWDED);
	}

	@Test
	@DisplayName("관측값이 없는 장소의 셀은 담기지 않는다")
	void omitsCellsWithoutObservation() {
		givenObservations(observation(1L, CongestionLevel.NORMAL));
		givenMappings(mapping(1L, 100L), mapping(2L, 200L));

		Map<Long, CongestionLevel> byCell = reader.latestByGridCell(WINDOW_START);

		// 200번 셀은 "혼잡도 0" 이 아니라 키가 없다 — 근거 없음과 여유는 다르다.
		assertThat(byCell).containsOnlyKeys(100L);
	}

	@Test
	@DisplayName("수집이 시간 창보다 오래됐으면 쓰지 않는다")
	void ignoresObservationOlderThanWindow() {
		// findLatestPerPlace 는 시각과 무관하게 장소별 최신 1건을 준다. 수집이 멈추면
		// 오래된 값이 계속 최신으로 잡히므로 창 밖은 여기서 걸러야 한다(명세 7.3).
		givenObservations(observation(
				1L, CongestionLevel.CROWDED, WINDOW_START.minus(Duration.ofMinutes(1))));
		givenMappings(mapping(1L, 100L));

		assertThat(reader.latestByGridCell(WINDOW_START)).isEmpty();
	}

	@Test
	@DisplayName("서울시 관측 시각이 창보다 오래돼도 방금 수집했으면 쓴다")
	void usesFreshlyCollectedObservationWithStaleObservedAt() {
		// 운영 회귀: PPLTN_TIME 은 수집 시각보다 28~31분 뒤쳐진다. observedAt 으로 자르면
		// 정상 수집 중에도 공공데이터 근거가 전멸했다(24시간 288배치 중 통과 0건).
		Place place = mock(Place.class);
		when(place.getId()).thenReturn(1L);
		CityCongestionObservation observation = mock(CityCongestionObservation.class);
		when(observation.getPlace()).thenReturn(place);
		when(observation.getCongestionLevel()).thenReturn(CongestionLevel.CROWDED);
		when(observation.getCollectedAt()).thenReturn(WINDOW_END);
		givenObservations(observation);
		givenMappings(mapping(1L, 100L));

		assertThat(reader.latestByGridCell(WINDOW_START))
				.containsEntry(100L, CongestionLevel.CROWDED);
	}

	@Test
	@DisplayName("창 시작과 같은 시각의 관측은 쓴다")
	void includesObservationAtWindowStart() {
		givenObservations(observation(1L, CongestionLevel.NORMAL, WINDOW_START));
		givenMappings(mapping(1L, 100L));

		assertThat(reader.latestByGridCell(WINDOW_START)).containsEntry(100L, CongestionLevel.NORMAL);
	}

	@Test
	@DisplayName("관측값이 하나도 없으면 매핑을 읽지 않는다")
	void skipsMappingsWhenNoObservation() {
		givenObservations();

		assertThat(reader.latestByGridCell(WINDOW_START)).isEmpty();
		// 7,346행을 읽을 이유가 없다.
		verify(mappings, never()).findAll();
	}

	private void givenObservations(CityCongestionObservation... values) {
		when(observations.findLatestPerPlace()).thenReturn(List.of(values));
	}

	private void givenMappings(PlaceGridMapping... values) {
		when(mappings.findAll()).thenReturn(List.of(values));
	}

	private static CityCongestionObservation observation(Long placeId, CongestionLevel level) {
		return observation(placeId, level, OBSERVED_AT);
	}

	/** 창 판정은 수집 시각으로 한다. 인자 이름을 그대로 collectedAt 으로 둔다. */
	private static CityCongestionObservation observation(
			Long placeId, CongestionLevel level, Instant collectedAt) {
		Place place = mock(Place.class);
		when(place.getId()).thenReturn(placeId);

		CityCongestionObservation observation = mock(CityCongestionObservation.class);
		when(observation.getPlace()).thenReturn(place);
		when(observation.getCongestionLevel()).thenReturn(level);
		when(observation.getCollectedAt()).thenReturn(collectedAt);
		return observation;
	}

	private static PlaceGridMapping mapping(Long placeId, Long gridCellId) {
		PlaceGridMapping mapping = mock(PlaceGridMapping.class);
		when(mapping.getPlaceId()).thenReturn(placeId);
		when(mapping.getGridCellId()).thenReturn(gridCellId);
		return mapping;
	}
}
