package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceBoundary;

class PlaceGridRasterizerTest {
	private final PlaceGridRasterizer rasterizer = new PlaceGridRasterizer();

	@Test
	@DisplayName("경계가 덮는 셀을 모두 찾는다")
	void findsEveryCoveredCell() {
		// 한 변 0.01도 정사각형. (37.50, 127.00) ~ (37.51, 127.01)
		List<GridCellCode> covered = rasterizer.cellsCovering(boundary(
				"[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]]"
		));

		// 위도 0.01도 / 0.0009 = 11칸, 경도 0.01도 / 0.00113 = 9칸
		assertThat(covered).hasSize(99);
		assertThat(covered).contains(new GridCellCode(111, 265), new GridCellCode(121, 273));
	}

	@Test
	@DisplayName("찾은 셀의 중심은 모두 경계 안이다")
	void everyCoveredCellHasCenterInside() {
		PlaceBoundary boundary = boundary(
				"[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]]"
		);

		for (GridCellCode cell : rasterizer.cellsCovering(boundary)) {
			assertThat(boundary.contains(
					cell.centerLongitude().doubleValue(),
					cell.centerLatitude().doubleValue()
			)).isTrue();
		}
	}

	@Test
	@DisplayName("구멍 안의 셀은 덮지 않는다")
	void skipsCellsInsideHole() {
		String outer = "[[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]]";
		String hole = "[[127.004,37.504],[127.006,37.504],[127.006,37.506],[127.004,37.506],[127.004,37.504]]";

		List<GridCellCode> withHole = rasterizer.cellsCovering(boundary(outer + "," + hole));
		List<GridCellCode> withoutHole = rasterizer.cellsCovering(boundary(outer));

		assertThat(withHole).hasSizeLessThan(withoutHole.size());
		assertThat(withHole).isSubsetOf(withoutHole);
	}

	@Test
	@DisplayName("셀 중심을 하나도 덮지 못하는 작은 경계도 셀 하나는 얻는다")
	void fallsBackToSingleCellWhenTooSmall() {
		// 한 변 0.0001도(약 11m) 정사각형. 어떤 셀 중심도 포함하지 못한다.
		List<GridCellCode> covered = rasterizer.cellsCovering(boundary(
				"[[127.0001,37.5001],[127.0002,37.5001],[127.0002,37.5002],[127.0001,37.5002],[127.0001,37.5001]]"
		));

		assertThat(covered).hasSize(1);
		assertThat(covered.get(0)).isEqualTo(GridCellCode.of(37.50015, 127.00015));
	}

	private static PlaceBoundary boundary(String rings) {
		Place place = mock(Place.class);
		when(place.getBoundaryGeoJson())
				.thenReturn("{\"type\":\"Polygon\",\"coordinates\":[" + rings + "]}");
		return PlaceBoundary.from(place).orElseThrow();
	}
}
