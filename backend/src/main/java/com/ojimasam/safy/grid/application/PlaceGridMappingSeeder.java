package com.ojimasam.safy.grid.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.PlaceBoundary;

/**
 * 121곳 장소가 덮는 격자 셀을 한 번 계산해 저장한다.
 *
 * <p>장소 경계는 시드로 들어오고 바뀌지 않으므로 매번 계산할 필요가 없다. 이미
 * 매핑이 있으면 아무것도 하지 않는다 — 장소를 새로 넣었다면 이 테이블을 비우고
 * 다시 띄운다.
 *
 * <p>장소보다 먼저 뜨면 경계가 0건이라 그냥 끝난다. 그때는 다음 기동에 다시
 * 시도하므로 순서를 강제하지 않는다.
 */
@Component
public class PlaceGridMappingSeeder implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(PlaceGridMappingSeeder.class);

	private final PlaceBoundaries placeBoundaries;
	private final PlaceGridRasterizer rasterizer;
	private final PlaceGridMappingRepository placeGridMappingRepository;
	private final GridCellRepository gridCellRepository;
	private final JdbcTemplate jdbcTemplate;

	public PlaceGridMappingSeeder(
			PlaceBoundaries placeBoundaries,
			PlaceGridRasterizer rasterizer,
			PlaceGridMappingRepository placeGridMappingRepository,
			GridCellRepository gridCellRepository,
			JdbcTemplate jdbcTemplate
	) {
		this.placeBoundaries = placeBoundaries;
		this.rasterizer = rasterizer;
		this.placeGridMappingRepository = placeGridMappingRepository;
		this.gridCellRepository = gridCellRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (placeGridMappingRepository.count() > 0) {
			return;
		}

		List<PlaceBoundary> boundaries = placeBoundaries.all();
		if (boundaries.isEmpty()) {
			log.info("장소 경계가 없어 격자 매핑을 건너뜁니다.");
			return;
		}

		Map<Long, List<GridCellCode>> cellsByPlace = new HashMap<>();
		Set<GridCellCode> distinctCells = new LinkedHashSet<>();
		for (PlaceBoundary boundary : boundaries) {
			List<GridCellCode> covered = rasterizer.cellsCovering(boundary);
			cellsByPlace.put(boundary.placeId(), covered);
			distinctCells.addAll(covered);
		}

		Map<String, Long> cellIdsByCode = createMissingCells(distinctCells);
		int mappingRows = insertMappings(cellsByPlace, cellIdsByCode);

		log.info(
				"장소 {}곳의 격자 매핑을 만들었습니다. 셀 {}개, 매핑 {}행",
				boundaries.size(), distinctCells.size(), mappingRows
		);
	}

	/**
	 * 셀 행을 확보하고 코드 → ID 를 돌려준다.
	 *
	 * <p>제보가 이미 만든 셀이 섞여 있으므로 전부 읽어 비교한다. 이 시점의
	 * {@code grid_cells} 는 제보가 닿은 셀뿐이라 크지 않다.
	 */
	private Map<String, Long> createMissingCells(Set<GridCellCode> cells) {
		Map<String, Long> idsByCode = new HashMap<>();
		for (GridCell existing : gridCellRepository.findAll()) {
			idsByCode.put(existing.getGridCode(), existing.getId());
		}

		List<Object[]> rows = new ArrayList<>();
		Timestamp now = Timestamp.from(Instant.now());
		for (GridCellCode cell : cells) {
			if (idsByCode.containsKey(cell.value())) {
				continue;
			}
			rows.add(new Object[] {
					cell.value(), cell.row(), cell.column(),
					cell.centerLatitude(), cell.centerLongitude(), now
			});
		}

		if (!rows.isEmpty()) {
			jdbcTemplate.batchUpdate(
					"INSERT INTO grid_cells "
							+ "(grid_code, row_index, column_index, center_latitude, center_longitude, created_at) "
							+ "VALUES (?, ?, ?, ?, ?, ?)",
					rows
			);
			// 방금 넣은 행의 ID 는 배치 삽입으로 얻을 수 없어 다시 읽는다.
			for (GridCell created : gridCellRepository.findAll()) {
				idsByCode.putIfAbsent(created.getGridCode(), created.getId());
			}
		}

		return idsByCode;
	}

	private int insertMappings(Map<Long, List<GridCellCode>> cellsByPlace, Map<String, Long> cellIdsByCode) {
		List<Object[]> rows = new ArrayList<>();
		Timestamp now = Timestamp.from(Instant.now());

		for (Map.Entry<Long, List<GridCellCode>> entry : cellsByPlace.entrySet()) {
			for (GridCellCode cell : entry.getValue()) {
				rows.add(new Object[] {entry.getKey(), cellIdsByCode.get(cell.value()), now});
			}
		}

		jdbcTemplate.batchUpdate(
				"INSERT INTO place_grid_mappings (place_id, grid_cell_id, created_at) VALUES (?, ?, ?)",
				rows
		);
		return rows.size();
	}
}
