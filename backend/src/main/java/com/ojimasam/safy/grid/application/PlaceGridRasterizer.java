package com.ojimasam.safy.grid.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.place.domain.PlaceBoundary;

/**
 * 장소 경계가 덮는 격자 셀을 찾는다.
 *
 * <p>셀 <b>중심</b>이 폴리곤 안이면 덮은 것으로 본다. 경계에 걸친 셀은 중심이 밖이면
 * 제외되므로 실제 경계보다 조금 작게 잡히지만, 격자가 100m 라 한 칸 차이는 위험도
 * 표시에 영향이 없고 규칙이 단순해 프론트에서 같은 결과를 재현하기 쉽다.
 */
@Component
public class PlaceGridRasterizer {
	/**
	 * 경계가 덮는 셀들. bbox 안의 셀만 훑고 중심으로 판정한다.
	 *
	 * <p>폴리곤이 셀보다 작거나 셀 중심을 비켜 가면 결과가 빌 수 있다. 그때는 bbox
	 * 중심이 속한 셀 하나를 넣는다 — 관측값이 있는 장소가 지도에서 통째로 사라지는
	 * 것보다 한 칸이라도 남는 편이 낫다.
	 */
	public List<GridCellCode> cellsCovering(PlaceBoundary boundary) {
		GridCellCode southWest = GridCellCode.of(boundary.minLatitude(), boundary.minLongitude());
		GridCellCode northEast = GridCellCode.of(boundary.maxLatitude(), boundary.maxLongitude());

		List<GridCellCode> covered = new ArrayList<>();
		for (int row = southWest.row(); row <= northEast.row(); row++) {
			for (int column = southWest.column(); column <= northEast.column(); column++) {
				GridCellCode candidate = new GridCellCode(row, column);
				boolean centerIsInside = boundary.contains(
						candidate.centerLongitude().doubleValue(),
						candidate.centerLatitude().doubleValue()
				);
				if (centerIsInside) {
					covered.add(candidate);
				}
			}
		}

		if (covered.isEmpty()) {
			covered.add(GridCellCode.of(
					(boundary.minLatitude() + boundary.maxLatitude()) / 2,
					(boundary.minLongitude() + boundary.maxLongitude()) / 2
			));
		}

		return List.copyOf(covered);
	}
}
