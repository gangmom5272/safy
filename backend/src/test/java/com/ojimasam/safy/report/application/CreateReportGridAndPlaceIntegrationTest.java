package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

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

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.infrastructure.JpaGridCellRepository;
import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.report.application.dto.CreateReportCommand;
import com.ojimasam.safy.report.application.dto.CreateReportResult;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.infrastructure.JpaReportRepository;

/**
 * 제보 등록이 격자 셀 코드와 장소를 <b>실제 컬럼에 저장하는지</b> 확인한다.
 *
 * <p>CreateReportServiceTest 는 리포지토리를 목으로 대체하므로 컬럼 매핑이 틀려도
 * 통과한다. 이 테스트는 실제 스키마에 저장한 뒤 다시 읽어 검증한다.
 *
 * <p>장소는 실제 시드(121곳)를 넣어 쓴다. 손으로 만든 사각형으로는 중첩·구멍 같은
 * 실제 데이터 특성을 확인할 수 없다.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateReportGridAndPlaceIntegrationTest {
	/** 홍대입구역(POI055) 폴리곤 안. 홍대 관광특구(POI007)와 겹치는 지점이다. */
	private static final BigDecimal HONGDAE_LATITUDE = new BigDecimal("37.5572000");
	private static final BigDecimal HONGDAE_LONGITUDE = new BigDecimal("126.9254000");

	@Autowired CreateReportService createReportService;
	@Autowired ReportRepository reports;
	@Autowired JpaReportRepository reportRepository;
	@Autowired PlaceRepository places;
	@Autowired PlaceBoundaries placeBoundaries;
	@Autowired JpaGridCellRepository gridCells;
	@Autowired DataSource dataSource;

	@BeforeEach
	void setUp() throws Exception {
		reportRepository.deleteAll();
		// 셀 행이 없는 상태에서 시작해야 "없으면 만든다"를 확인할 수 있다.
		gridCells.deleteAll();

		if (places.findAllByActiveTrueOrderByNameAsc().isEmpty()) {
			new JdbcTemplate(dataSource).execute(placeInsertStatement());
		}
		// 경계는 첫 호출에 캐시된다. 다른 테스트가 빈 목록을 캐시했을 수 있어 비운다.
		ReflectionTestUtils.setField(placeBoundaries, "cached", null);
	}

	@Test
	@DisplayName("등록한 제보를 다시 읽으면 격자 셀 코드와 장소가 저장돼 있다")
	void persistsGridCellCodeAndPlace() {
		CreateReportResult result = createReportService.create(new CreateReportCommand(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION,
				"홍대입구역 9번 출구 혼잡",
				HONGDAE_LATITUDE,
				HONGDAE_LONGITUDE
		));

		Report saved = reports.findOneById(result.reportId()).orElseThrow();

		assertThat(saved.getGridCellCode()).isEqualTo("S100-r174-c199");
		assertThat(saved.getPlaceId()).isNotNull();

		Place located = places.findAllByActiveTrueOrderByNameAsc().stream()
				.filter(place -> place.getId().equals(saved.getPlaceId()))
				.findFirst()
				.orElseThrow();
		// 겹치는 장소 중 더 작은 쪽이 선택된다(홍대 관광특구가 아니라 홍대입구역).
		assertThat(located.getExternalPlaceId()).isEqualTo("POI055");
	}

	@Test
	@DisplayName("제보가 처음 닿은 셀은 grid_cells 행으로 생성된다")
	void createsGridCellRowOnFirstReport() {
		assertThat(gridCells.findByGridCode("S100-r174-c199")).isEmpty();

		createReportService.create(new CreateReportCommand(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION,
				"홍대입구역 9번 출구 혼잡",
				HONGDAE_LATITUDE,
				HONGDAE_LONGITUDE
		));

		GridCell cell = gridCells.findByGridCode("S100-r174-c199").orElseThrow();
		assertThat(cell.getRowIndex()).isEqualTo(174);
		assertThat(cell.getColumnIndex()).isEqualTo(199);
	}

	@Test
	@DisplayName("같은 셀에 제보가 또 들어와도 셀 행은 하나만 남는다")
	void reusesGridCellRowForSecondReportInSameCell() {
		for (int i = 0; i < 2; i++) {
			createReportService.create(new CreateReportCommand(
					1L,
					ReportType.IMAGE,
					ReportCategory.CROWD_CONGESTION,
					ReportSubcategory.EXIT_CONGESTION,
					"홍대입구역 9번 출구 혼잡",
					HONGDAE_LATITUDE,
					HONGDAE_LONGITUDE
			));
		}

		assertThat(gridCells.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("어느 장소에도 들지 않는 좌표는 장소 없이 셀 코드만 저장된다")
	void persistsGridCellCodeWithoutPlace() {
		// 서울 밖(부산). 격자 셀은 음수 인덱스로 나온다.
		CreateReportResult result = createReportService.create(new CreateReportCommand(
				1L,
				ReportType.IMAGE,
				ReportCategory.OTHER,
				ReportSubcategory.OTHER_SITUATION,
				"장소 밖 제보",
				new BigDecimal("35.1796000"),
				new BigDecimal("129.0756000")
		));

		Report saved = reports.findOneById(result.reportId()).orElseThrow();

		assertThat(saved.getGridCellCode()).isEqualTo("S100-r-2468-c2102");
		assertThat(saved.getPlaceId()).isNull();
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
