package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.grid.infrastructure.JpaGridCellRepository;
import com.ojimasam.safy.grid.infrastructure.JpaPlaceGridMappingRepository;
import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.PlaceRepository;

/**
 * 시드가 실제 스키마에 매핑을 채우는지, 다시 돌려도 늘지 않는지 확인한다.
 *
 * <p>부팅마다 도는 코드라 재실행 안전성이 정확성만큼 중요하다.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlaceGridMappingSeederTest {
	@Autowired PlaceGridMappingSeeder seeder;
	@Autowired PlaceGridMappingRepository mappings;
	@Autowired JpaPlaceGridMappingRepository mappingRepository;
	@Autowired JpaGridCellRepository gridCells;
	@Autowired PlaceBoundaries placeBoundaries;
	@Autowired PlaceRepository places;
	@Autowired DataSource dataSource;

	@BeforeEach
	void setUp() throws Exception {
		mappingRepository.deleteAll();
		gridCells.deleteAll();

		if (places.findAllByActiveTrueOrderByNameAsc().isEmpty()) {
			new JdbcTemplate(dataSource).execute(placeInsertStatement());
		}
		ReflectionTestUtils.setField(placeBoundaries, "cached", null);
	}

	@Test
	@DisplayName("121곳의 격자 매핑을 채운다")
	void seedsMappingsForEveryPlace() {
		seeder.run(null);

		// 2026-08-02 실측: 셀 6,782개, 매핑 7,346행.
		assertThat(gridCells.count()).isEqualTo(6782);
		assertThat(mappings.count()).isEqualTo(7346);

		// 장소마다 최소 한 개씩 걸린다.
		List<Long> mappedPlaceIds = mappingRepository.findAll().stream()
				.map(mapping -> mapping.getPlaceId())
				.distinct()
				.toList();
		assertThat(mappedPlaceIds).hasSize(121);
	}

	@Test
	@DisplayName("이미 매핑이 있으면 다시 만들지 않는다")
	void doesNothingWhenAlreadySeeded() {
		seeder.run(null);
		long cellsAfterFirst = gridCells.count();
		long mappingsAfterFirst = mappings.count();

		seeder.run(null);

		assertThat(gridCells.count()).isEqualTo(cellsAfterFirst);
		assertThat(mappings.count()).isEqualTo(mappingsAfterFirst);
	}

	@Test
	@DisplayName("제보가 이미 만든 셀은 다시 만들지 않고 그대로 쓴다")
	void reusesCellsCreatedByReports() {
		// 홍대입구역 폴리곤 안의 셀. 제보가 먼저 만든 상황을 흉내낸다.
		new JdbcTemplate(dataSource).update(
				"INSERT INTO grid_cells "
						+ "(grid_code, row_index, column_index, center_latitude, center_longitude, created_at) "
						+ "VALUES ('S100-r174-c199', 174, 199, 37.5568500, 126.9251650, CURRENT_TIMESTAMP)"
		);
		Long existingId = gridCells.findByGridCode("S100-r174-c199").orElseThrow().getId();

		seeder.run(null);

		assertThat(gridCells.findByGridCode("S100-r174-c199").orElseThrow().getId()).isEqualTo(existingId);
		assertThat(gridCells.count()).isEqualTo(6782);
		// 그 셀을 가리키는 매핑이 생겼다.
		assertThat(mappingRepository.findAll().stream()
				.anyMatch(mapping -> mapping.getGridCellId().equals(existingId))).isTrue();
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
