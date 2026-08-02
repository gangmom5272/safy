package com.ojimasam.safy.grid.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GridCellCodeTest {
	/**
	 * 프론트·백엔드 공유 테스트 벡터.
	 *
	 * <p>이 표는 프론트({@code frontend-admin/src/pages/risk-map/gridRisk.ts}) 산식으로
	 * 생성했다. 프론트에도 같은 표로 테스트를 두어, 한쪽 산식이 바뀌면 양쪽 CI 가
	 * 함께 깨지게 한다. 격자 작업에서 가장 조용히 깨지는 지점이 여기다.
	 *
	 * <p>표를 수정할 때는 임의로 숫자를 고치지 말고 프론트 산식으로 다시 생성한다.
	 */
	private static final List<Vector> SHARED_VECTORS = List.of(
			vector("서울시청", 37.5665, 126.978, 184, 246, "S100-r184-c246"),
			vector("홍대입구역", 37.5572, 126.9254, 174, 199, "S100-r174-c199"),
			vector("강남역", 37.4979, 127.0276, 108, 289, "S100-r108-c289"),
			vector("원점 정확히", 37.4, 126.7, 0, 0, "S100-r0-c0"),
			vector("원점 남서쪽 한 칸", 37.3999, 126.6999, -1, -1, "S100-r-1-c-1"),
			vector("위도 셀 경계 정확히", 37.4009, 126.7, 1, 0, "S100-r1-c0"),
			vector("위도 셀 경계 직전", 37.40089999, 126.7, 0, 0, "S100-r0-c0"),
			vector("경도 셀 경계 정확히", 37.4, 126.70113, 0, 1, "S100-r0-c1"),
			vector("경도 셀 경계 직전", 37.4, 126.70112999, 0, 0, "S100-r0-c0"),
			vector("홍대 북쪽 100m", 37.5581, 126.9254, 175, 199, "S100-r175-c199"),
			vector("홍대 동쪽 100m", 37.5572, 126.92653, 174, 200, "S100-r174-c200"),
			vector("서울 남서 끝", 37.413, 126.764, 14, 56, "S100-r14-c56"),
			vector("서울 북동 끝", 37.715, 127.184, 350, 428, "S100-r350-c428"),
			vector("부산(서울 밖)", 35.1796, 129.0756, -2468, 2102, "S100-r-2468-c2102"),
			vector("원점 훨씬 남서(음수)", 37.39, 126.69, -12, -9, "S100-r-12-c-9")
	);

	@Test
	@DisplayName("프론트와 같은 산식으로 같은 셀 코드를 낸다")
	void matchesSharedVectors() {
		for (Vector expected : SHARED_VECTORS) {
			GridCellCode actual = GridCellCode.of(expected.latitude(), expected.longitude());

			assertThat(actual.row()).as("%s row", expected.name()).isEqualTo(expected.row());
			assertThat(actual.column()).as("%s column", expected.name()).isEqualTo(expected.column());
			assertThat(actual.value()).as("%s code", expected.name()).isEqualTo(expected.code());
		}
	}

	@Test
	@DisplayName("BigDecimal 좌표도 double 과 같은 셀을 낸다")
	void acceptsBigDecimalCoordinates() {
		for (Vector expected : SHARED_VECTORS) {
			GridCellCode fromDecimal = GridCellCode.of(
					BigDecimal.valueOf(expected.latitude()),
					BigDecimal.valueOf(expected.longitude())
			);

			assertThat(fromDecimal.value()).as(expected.name()).isEqualTo(expected.code());
		}
	}

	@Test
	@DisplayName("같은 좌표는 항상 같은 셀 코드를 낸다")
	void isDeterministic() {
		GridCellCode first = GridCellCode.of(37.5572, 126.9254);
		GridCellCode second = GridCellCode.of(37.5572, 126.9254);

		assertThat(second).isEqualTo(first);
		assertThat(second.value()).isEqualTo(first.value());
	}

	@Test
	@DisplayName("100m 떨어진 좌표는 다른 셀이 된다")
	void separatesCellsAtHundredMeters() {
		GridCellCode base = GridCellCode.of(37.5572, 126.9254);

		assertThat(GridCellCode.of(37.5572 + GridCellCode.STEP_LATITUDE, 126.9254))
				.isNotEqualTo(base);
		assertThat(GridCellCode.of(37.5572, 126.9254 + GridCellCode.STEP_LONGITUDE))
				.isNotEqualTo(base);
	}

	@Test
	@DisplayName("음수 인덱스에서도 코드가 한 가지로만 읽힌다")
	void keepsNegativeIndexCodeUnambiguous() {
		String code = GridCellCode.of(37.39, 126.69).value();

		assertThat(code).isEqualTo("S100-r-12-c-9");
		// 접두사가 없으면 'S100--12--9' 가 되어 dash 가 겹친다.
		assertThat(code).doesNotContain("--");
	}

	@Test
	@DisplayName("셀 중심 좌표를 컬럼 scale 로 돌려준다")
	void computesCenterAtColumnScale() {
		GridCellCode cell = GridCellCode.of(37.5572, 126.9254);

		assertThat(cell.centerLatitude()).isEqualByComparingTo("37.5570500");
		assertThat(cell.centerLongitude()).isEqualByComparingTo("126.9254350");
		assertThat(cell.centerLatitude().scale()).isEqualTo(7);
		assertThat(cell.centerLongitude().scale()).isEqualTo(7);
	}

	@Test
	@DisplayName("셀 중심 좌표는 그 셀 안에 있다")
	void centerBelongsToSameCell() {
		for (Vector expected : SHARED_VECTORS) {
			GridCellCode cell = GridCellCode.of(expected.latitude(), expected.longitude());
			GridCellCode fromCenter = GridCellCode.of(cell.centerLatitude(), cell.centerLongitude());

			assertThat(fromCenter).as(expected.name()).isEqualTo(cell);
		}
	}

	@Test
	@DisplayName("좌표가 없으면 셀을 만들지 않는다")
	void rejectsMissingCoordinates() {
		assertThatThrownBy(() -> GridCellCode.of(null, BigDecimal.ONE))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("위도와 경도는 필수입니다.");
		assertThatThrownBy(() -> GridCellCode.of(BigDecimal.ONE, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("위도와 경도는 필수입니다.");
	}

	private static Vector vector(
			String name,
			double latitude,
			double longitude,
			int row,
			int column,
			String code
	) {
		return new Vector(name, latitude, longitude, row, column, code);
	}

	private record Vector(
			String name,
			double latitude,
			double longitude,
			int row,
			int column,
			String code
	) {
	}
}
