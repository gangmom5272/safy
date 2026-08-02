package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.PlaceBoundary;
import com.ojimasam.safy.place.domain.PlaceRepository;

/**
 * 실제 시드 121곳을 rasterize 했을 때의 규모와 빈틈을 확인한다.
 *
 * <p>손으로 만든 사각형으로는 알 수 없는 것들이 있다 — 장소마다 크기가 100배 넘게
 * 차이 나고(강남역 9정점, 남산공원 1084정점), 셀보다 작아 중심을 못 덮는 장소가
 * 있는지, 겹침 때문에 매핑 행이 셀 수보다 얼마나 많아지는지가 그렇다.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlaceGridRasterizerSeedTest {
	private static final Logger log = LoggerFactory.getLogger(PlaceGridRasterizerSeedTest.class);

	@Autowired PlaceGridRasterizer rasterizer;
	@Autowired PlaceBoundaries placeBoundaries;
	@Autowired PlaceRepository places;
	@Autowired DataSource dataSource;

	@BeforeEach
	void setUp() throws Exception {
		if (places.findAllByActiveTrueOrderByNameAsc().isEmpty()) {
			new JdbcTemplate(dataSource).execute(placeInsertStatement());
		}
		// 첫 호출에 캐시한다. 다른 테스트가 빈 목록을 캐시했을 수 있어 비운다.
		ReflectionTestUtils.setField(placeBoundaries, "cached", null);
	}

	@Test
	@DisplayName("121곳 전부가 최소 한 개 셀에 매핑된다")
	void everyPlaceCoversAtLeastOneCell() {
		List<PlaceBoundary> boundaries = placeBoundaries.all();
		Set<GridCellCode> distinctCells = new HashSet<>();
		int mappingRows = 0;
		int fallbackPlaces = 0;

		for (PlaceBoundary boundary : boundaries) {
			List<GridCellCode> covered = rasterizer.cellsCovering(boundary);
			assertThat(covered).isNotEmpty();

			// 셀 중심을 하나도 못 덮어 fallback 으로 한 칸만 받은 장소를 센다.
			if (covered.size() == 1
					&& !boundary.contains(
							covered.get(0).centerLongitude().doubleValue(),
							covered.get(0).centerLatitude().doubleValue())) {
				fallbackPlaces++;
			}

			distinctCells.addAll(covered);
			mappingRows += covered.size();
		}

		log.info(
				"장소 {}곳 → 셀 {}개, 매핑 {}행, fallback {}곳",
				boundaries.size(), distinctCells.size(), mappingRows, fallbackPlaces
		);

		// 2026-08-02 실측: 121곳 → 셀 6,782개, 매핑 7,346행, fallback 0곳.
		// 정확한 수치를 고정하지 않는 이유는 시드가 갱신되면 함께 변하기 때문이다.
		// 규모가 크게 달라지면 로그로 확인한다.
		assertThat(boundaries).hasSize(121);
		assertThat(distinctCells).isNotEmpty();
		// 겹치는 장소가 있으므로 매핑 행은 서로 다른 셀 수보다 많다.
		assertThat(mappingRows).isGreaterThan(distinctCells.size());
		// 현재 시드에서는 모든 장소가 셀 중심을 최소 하나 덮는다. fallback 은
		// 장소가 추가·축소될 때를 위한 것이지 지금 쓰이는 경로가 아니다.
		assertThat(fallbackPlaces).isZero();
	}

	/**
	 * 시드 끝의 {@code ON CONFLICT ... DO UPDATE} 는 H2 가 지원하지 않아 잘라내고
	 * INSERT 본문만 실행한다. SeoulPlacesSeedTest 와 같은 방식이다.
	 */
	private static String placeInsertStatement() throws Exception {
		String sql = new String(
				new ClassPathResource("db/seed/seoul-places.sql").getInputStream().readAllBytes(),
				StandardCharsets.UTF_8
		);
		int insertStart = sql.indexOf("INSERT INTO places");
		int conflictStart = sql.indexOf("ON CONFLICT", insertStart);
		return sql.substring(insertStart, conflictStart).trim();
	}
}
