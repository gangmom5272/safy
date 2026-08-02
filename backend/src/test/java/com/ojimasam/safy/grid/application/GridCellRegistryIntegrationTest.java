package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.infrastructure.JpaGridCellRepository;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GridCellRegistryIntegrationTest {
	private static final BigDecimal HONGDAE_LATITUDE = new BigDecimal("37.5572000");
	private static final BigDecimal HONGDAE_LONGITUDE = new BigDecimal("126.9254000");

	@Autowired GridCellRegistry registry;
	@Autowired JpaGridCellRepository gridCellRepository;

	@BeforeEach
	void cleanUp() {
		gridCellRepository.deleteAll();
	}

	@Test
	@DisplayName("셀 행이 없으면 만들고 좌표계 값을 그대로 채운다")
	void createsCellWhenMissing() {
		GridCell cell = registry.findOrCreate(HONGDAE_LATITUDE, HONGDAE_LONGITUDE);

		assertThat(cell.getId()).isNotNull();
		assertThat(cell.getGridCode()).isEqualTo("S100-r174-c199");
		assertThat(cell.getRowIndex()).isEqualTo(174);
		assertThat(cell.getColumnIndex()).isEqualTo(199);
		assertThat(cell.getCenterLatitude()).isEqualByComparingTo("37.5570500");
		assertThat(cell.getCenterLongitude()).isEqualByComparingTo("126.9254350");
		assertThat(cell.getCreatedAt()).isNotNull();
		assertThat(gridCellRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 셀 안의 다른 좌표는 이미 있는 행을 다시 쓴다")
	void reusesCellForAnotherCoordinateInSameCell() {
		GridCell first = registry.findOrCreate(HONGDAE_LATITUDE, HONGDAE_LONGITUDE);
		// 같은 셀 안에서 조금 움직인 좌표(약 20m)
		GridCell second = registry.findOrCreate(
				new BigDecimal("37.5573000"),
				new BigDecimal("126.9256000")
		);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(gridCellRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("100m 떨어진 좌표는 새 행을 만든다")
	void createsAnotherCellHundredMetersAway() {
		registry.findOrCreate(HONGDAE_LATITUDE, HONGDAE_LONGITUDE);
		GridCell north = registry.findOrCreate(
				new BigDecimal("37.5581000"),
				HONGDAE_LONGITUDE
		);

		assertThat(north.getGridCode()).isEqualTo("S100-r175-c199");
		assertThat(gridCellRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("서울 밖 좌표도 음수 인덱스 셀로 저장된다")
	void storesCellOutsideSeoul() {
		GridCell busan = registry.findOrCreate(
				new BigDecimal("35.1796000"),
				new BigDecimal("129.0756000")
		);

		assertThat(busan.getGridCode()).isEqualTo("S100-r-2468-c2102");
		assertThat(busan.getRowIndex()).isNegative();
	}

	@Test
	@DisplayName("같은 셀에 동시 제보가 들어와도 행이 하나만 생긴다")
	void concurrentCreationInsertsSingleRow() throws Exception {
		int threads = 8;
		GridCellCode code = GridCellCode.of(HONGDAE_LATITUDE, HONGDAE_LONGITUDE);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);

		try {
			List<Future<String>> results = new java.util.ArrayList<>();
			for (int index = 0; index < threads; index++) {
				Callable<String> task = () -> {
					start.await(5, TimeUnit.SECONDS);
					return registry.findOrCreate(code).getGridCode();
				};
				results.add(executor.submit(task));
			}
			start.countDown();

			for (Future<String> result : results) {
				assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("S100-r174-c199");
			}
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}

		assertThat(gridCellRepository.count()).isEqualTo(1);
	}
}
