package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	private final CityCongestionObservationRepository observations =
			mock(CityCongestionObservationRepository.class);
	private final PlaceGridMappingRepository mappings = mock(PlaceGridMappingRepository.class);
	private final GridCongestionReader reader = new GridCongestionReader(observations, mappings);

	@Test
	@DisplayName("장소의 혼잡도를 그 장소가 덮는 셀 전부에 붙인다")
	void spreadsPlaceCongestionAcrossItsCells() {
		givenObservations(observation(1L, CongestionLevel.CROWDED));
		givenMappings(mapping(1L, 100L), mapping(1L, 101L), mapping(1L, 102L));

		Map<Long, CongestionLevel> byCell = reader.latestByGridCell();

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

		assertThat(reader.latestByGridCell()).containsEntry(100L, CongestionLevel.SLIGHTLY_CROWDED);
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

		assertThat(reader.latestByGridCell()).containsEntry(100L, CongestionLevel.CROWDED);
	}

	@Test
	@DisplayName("관측값이 없는 장소의 셀은 담기지 않는다")
	void omitsCellsWithoutObservation() {
		givenObservations(observation(1L, CongestionLevel.NORMAL));
		givenMappings(mapping(1L, 100L), mapping(2L, 200L));

		Map<Long, CongestionLevel> byCell = reader.latestByGridCell();

		// 200번 셀은 "혼잡도 0" 이 아니라 키가 없다 — 근거 없음과 여유는 다르다.
		assertThat(byCell).containsOnlyKeys(100L);
	}

	@Test
	@DisplayName("관측값이 하나도 없으면 매핑을 읽지 않는다")
	void skipsMappingsWhenNoObservation() {
		givenObservations();

		assertThat(reader.latestByGridCell()).isEmpty();
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
		Place place = mock(Place.class);
		when(place.getId()).thenReturn(placeId);

		CityCongestionObservation observation = mock(CityCongestionObservation.class);
		when(observation.getPlace()).thenReturn(place);
		when(observation.getCongestionLevel()).thenReturn(level);
		return observation;
	}

	private static PlaceGridMapping mapping(Long placeId, Long gridCellId) {
		PlaceGridMapping mapping = mock(PlaceGridMapping.class);
		when(mapping.getPlaceId()).thenReturn(placeId);
		when(mapping.getGridCellId()).thenReturn(gridCellId);
		return mapping;
	}
}
