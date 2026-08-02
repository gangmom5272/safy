package com.ojimasam.safy.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;

class PlaceLocatorTest {
	private final PlaceRepository placeRepository = mock(PlaceRepository.class);
	private final PlaceLocator locator = new PlaceLocator(placeRepository);

	/** 한 변 0.01도 정사각형. (37.50, 127.00) ~ (37.51, 127.01) */
	private static final String SQUARE = polygon(
			"[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]]"
	);

	@Test
	@DisplayName("좌표를 포함하는 장소를 찾는다")
	void findsContainingPlace() {
		givenPlaces(place(7L, "POI007", SQUARE));

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).contains(7L);
	}

	@Test
	@DisplayName("어느 장소에도 들지 않으면 빈 값이다")
	void returnsEmptyOutsideEveryPlace() {
		givenPlaces(place(7L, "POI007", SQUARE));

		assertThat(locator.locate(decimal("37.600"), decimal("127.100"))).isEmpty();
	}

	@Test
	@DisplayName("장소가 겹치면 가장 작은 장소를 고른다")
	void prefersSmallestPlaceWhenNested() {
		// 홍대입구역이 홍대 관광특구 안에 있는 것과 같은 구조다.
		String inner = polygon(
				"[[127.004,37.504],[127.006,37.504],[127.006,37.506],[127.004,37.506],[127.004,37.504]]"
		);
		givenPlaces(
				place(1L, "POI-OUTER", SQUARE),
				place(2L, "POI-INNER", inner)
		);

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).contains(2L);
		// 작은 장소 밖이면 큰 장소로 떨어진다.
		assertThat(locator.locate(decimal("37.502"), decimal("127.002"))).contains(1L);
	}

	@Test
	@DisplayName("구멍 안의 좌표는 그 장소에 속하지 않는다")
	void excludesCoordinateInsideHole() {
		// 121장소 중 북서울꿈의숲 한 곳에 구멍이 있다.
		String withHole = polygon(
				"[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]],"
						+ "[[127.004,37.504],[127.006,37.504],[127.006,37.506],[127.004,37.506],[127.004,37.504]]"
		);
		givenPlaces(place(9L, "POI096", withHole));

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).isEmpty();
		assertThat(locator.locate(decimal("37.502"), decimal("127.002"))).contains(9L);
	}

	@Test
	@DisplayName("경계가 비어 있는 장소는 건너뛴다")
	void skipsPlaceWithoutBoundary() {
		givenPlaces(place(1L, "POI-NULL", null), place(2L, "POI-BLANK", "  "), place(3L, "POI003", SQUARE));

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).contains(3L);
	}

	@Test
	@DisplayName("경계가 깨진 장소는 건너뛰고 나머지는 판정한다")
	void skipsBrokenBoundaryAndKeepsGoing() {
		givenPlaces(place(1L, "POI-BROKEN", "{\"type\":\"Polygon\""), place(2L, "POI002", SQUARE));

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).contains(2L);
	}

	@Test
	@DisplayName("Polygon 이 아닌 경계는 건너뛴다")
	void skipsNonPolygonGeometry() {
		String point = "{\"type\":\"Point\",\"coordinates\":[127.005,37.505]}";
		givenPlaces(place(1L, "POI-POINT", point), place(2L, "POI002", SQUARE));

		assertThat(locator.locate(decimal("37.505"), decimal("127.005"))).contains(2L);
	}

	@Test
	@DisplayName("좌표가 없으면 조회하지 않고 빈 값을 준다")
	void returnsEmptyWithoutCoordinates() {
		assertThat(locator.locate(null, decimal("127.005"))).isEmpty();
		assertThat(locator.locate(decimal("37.505"), null)).isEmpty();

		verify(placeRepository, times(0)).findAllByActiveTrueOrderByNameAsc();
	}

	@Test
	@DisplayName("경계는 한 번만 읽어 캐시한다")
	void loadsBoundariesOnce() {
		givenPlaces(place(7L, "POI007", SQUARE));

		locator.locate(decimal("37.505"), decimal("127.005"));
		locator.locate(decimal("37.506"), decimal("127.006"));
		locator.locate(decimal("37.900"), decimal("127.900"));

		verify(placeRepository, times(1)).findAllByActiveTrueOrderByNameAsc();
	}

	private void givenPlaces(Place... places) {
		when(placeRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(places));
	}

	private static Place place(Long id, String externalPlaceId, String boundaryGeoJson) {
		Place place = mock(Place.class);
		when(place.getId()).thenReturn(id);
		when(place.getExternalPlaceId()).thenReturn(externalPlaceId);
		when(place.getBoundaryGeoJson()).thenReturn(boundaryGeoJson);
		return place;
	}

	private static String polygon(String rings) {
		return "{\"type\":\"Polygon\",\"coordinates\":[" + rings + "]}";
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
