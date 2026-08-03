package com.ojimasam.safy.grid.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.grid.application.dto.GridRiskResponse;
import com.ojimasam.safy.grid.application.dto.GridRiskTrendPoint;
import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.grid.domain.GridRiskHistory;
import com.ojimasam.safy.grid.domain.GridRiskHistoryRepository;

/** 지도에 표시할 격자 위험도를 읽는다. */
@Service
public class GridRiskQueryService {
	/**
	 * 줌 아웃 시 묶을 수 있는 최대 배수. 16이면 한 칸이 1.6km 다.
	 * 이보다 크게 묶으면 위험 지점의 위치를 알 수 없어 지도로서 의미가 없다.
	 */
	public static final int MAX_RESOLUTION = 16;

	/**
	 * 한 번에 조회할 수 있는 최대 범위(도). 서울 전체가 위도 약 0.25도, 경도 약 0.35도라
	 * 넉넉히 두 배로 잡았다. 인증 없이 열린 API 라 상한이 없으면 요청 하나가 전체 스캔이 된다.
	 */
	static final double MAX_LATITUDE_SPAN = 0.5;
	static final double MAX_LONGITUDE_SPAN = 0.7;

	private final GridCellRepository gridCellRepository;
	private final GridRiskHistoryRepository gridRiskHistoryRepository;
	private final Clock clock;

	@Autowired
	public GridRiskQueryService(
			GridCellRepository gridCellRepository,
			GridRiskHistoryRepository gridRiskHistoryRepository
	) {
		this(gridCellRepository, gridRiskHistoryRepository, Clock.systemUTC());
	}

	GridRiskQueryService(
			GridCellRepository gridCellRepository,
			GridRiskHistoryRepository gridRiskHistoryRepository,
			Clock clock
	) {
		this.gridCellRepository = gridCellRepository;
		this.gridRiskHistoryRepository = gridRiskHistoryRepository;
		this.clock = clock;
	}

	/**
	 * 뷰포트 안 격자의 최신 위험도. 값이 없는 칸은 아예 빠진다.
	 *
	 * <p>뷰포트를 <b>격자 경계로 스냅</b>해서 조회한다. 지도를 1픽셀 움직여도 좌표가
	 * 달라지는데, 스냅하면 같은 칸 범위를 보는 동안 조회 결과가 같아져 캐시가 유효하다.
	 *
	 * <p>{@code resolution} 이 1보다 크면 그 배수로 묶어 <b>최댓값</b>을 낸다. 100m 기준
	 * 계산 결과를 묶는 것이고, 큰 칸에서 다시 계산하지 않는다 — 그러면 위험 지점이 칸
	 * 중심에서 밀려나 위험도가 사라져 보인다. 묶어 보내면 줌 아웃일수록 응답이 작아진다.
	 *
	 * <p>{@code grid_cells} 를 먼저 좁힌 뒤 이력을 읽는다. 두 테이블이 JPA 연관관계 없이
	 * {@code grid_cell_id} 로만 이어져 조인이 되지 않는다.
	 */
	@Transactional(readOnly = true)
	public List<GridRiskResponse> risksInBounds(
			BigDecimal minLatitude, BigDecimal maxLatitude,
			BigDecimal minLongitude, BigDecimal maxLongitude,
			int resolution
	) {
		validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);

		int merge = Math.clamp(resolution, 1, MAX_RESOLUTION);
		SnappedBounds snapped = SnappedBounds.of(
				minLatitude, maxLatitude, minLongitude, maxLongitude, merge);

		List<GridCell> cells = gridCellRepository
				.findAllByCenterLatitudeBetweenAndCenterLongitudeBetween(
						snapped.south(), snapped.north(), snapped.west(), snapped.east());
		if (cells.isEmpty()) {
			return List.of();
		}

		Map<Long, GridCell> cellsById = new HashMap<>();
		cells.forEach(cell -> cellsById.put(cell.getId(), cell));

		// 묶인 칸마다 점수가 가장 높은 100m 칸만 남긴다.
		Map<String, Aggregate> peaks = new HashMap<>();
		for (GridRiskHistory history : gridRiskHistoryRepository.findLatestByGridCellIdIn(cellsById.keySet())) {
			// 마지막 이력이 NO_DATA 면 위험이 끝난 칸이다. 그리지 않는다.
			if (!history.isActive()) {
				continue;
			}

			GridCell cell = cellsById.get(history.getGridCellId());
			if (cell == null) {
				continue;
			}

			int row = Math.floorDiv(cell.getRowIndex(), merge);
			int column = Math.floorDiv(cell.getColumnIndex(), merge);
			String key = row + ":" + column;

			Aggregate current = peaks.get(key);
			if (current == null || history.getScore() > current.peak().getScore()) {
				peaks.put(key, new Aggregate(row, column, history, cell.getGridCode()));
			}
		}

		List<GridRiskResponse> risks = new ArrayList<>(peaks.size());
		peaks.values().forEach(aggregate -> risks.add(GridRiskResponse.of(
				aggregate.row(), aggregate.column(), merge, aggregate.peak(), aggregate.peakCode())));
		return risks;
	}

	/**
	 * 한 격자의 위험도 추이. 최근 것부터다.
	 *
	 * <p>셀 행이 없거나 이력이 없으면 빈 목록이다 — 존재하지 않는 셀과 데이터가 없는 셀을
	 * 구분하지 않는다. 셀 코드는 좌표에서 결정론적으로 나오므로 모든 코드가 유효하고,
	 * 행이 없다는 것은 "그 칸에 아직 아무 일도 없었다"는 뜻이다.
	 */
	@Transactional(readOnly = true)
	public List<GridRiskTrendPoint> trend(String gridCode, Duration lookback) {
		Instant after = clock.instant().minus(lookback);

		return gridCellRepository.findByGridCode(gridCode)
				.map(cell -> gridRiskHistoryRepository
						.findAllByGridCellIdAndWindowEndedAtAfterOrderByWindowEndedAtDesc(
								cell.getId(), after))
				.orElseGet(List::of)
				.stream()
				.map(GridRiskTrendPoint::from)
				.toList();
	}

	private static void validateBounds(
			BigDecimal minLatitude, BigDecimal maxLatitude,
			BigDecimal minLongitude, BigDecimal maxLongitude
	) {
		if (minLatitude.compareTo(maxLatitude) >= 0 || minLongitude.compareTo(maxLongitude) >= 0) {
			throw new BusinessException(GridRiskErrorCode.INVALID_BOUNDS);
		}

		double latitudeSpan = maxLatitude.subtract(minLatitude).doubleValue();
		double longitudeSpan = maxLongitude.subtract(minLongitude).doubleValue();
		if (latitudeSpan > MAX_LATITUDE_SPAN || longitudeSpan > MAX_LONGITUDE_SPAN) {
			throw new BusinessException(GridRiskErrorCode.BOUNDS_TOO_WIDE);
		}
	}

	private record Aggregate(int row, int column, GridRiskHistory peak, String peakCode) {
	}

	/**
	 * 뷰포트를 묶음 칸 경계까지 넓힌 범위.
	 *
	 * <p>화면에 반쯤 걸친 칸도 그려야 하므로 바깥으로 넓힌다. 좁히면 화면 가장자리에
	 * 빈 띠가 생긴다.
	 */
	private record SnappedBounds(BigDecimal south, BigDecimal north, BigDecimal west, BigDecimal east) {
		static SnappedBounds of(
				BigDecimal minLatitude, BigDecimal maxLatitude,
				BigDecimal minLongitude, BigDecimal maxLongitude,
				int merge
		) {
			double heightDegrees = GridCellCode.STEP_LATITUDE * merge;
			double widthDegrees = GridCellCode.STEP_LONGITUDE * merge;

			return new SnappedBounds(
					snapDown(minLatitude, GridCellCode.ORIGIN_LATITUDE, heightDegrees),
					snapUp(maxLatitude, GridCellCode.ORIGIN_LATITUDE, heightDegrees),
					snapDown(minLongitude, GridCellCode.ORIGIN_LONGITUDE, widthDegrees),
					snapUp(maxLongitude, GridCellCode.ORIGIN_LONGITUDE, widthDegrees)
			);
		}

		private static BigDecimal snapDown(BigDecimal value, double origin, double step) {
			double index = Math.floor((value.doubleValue() - origin) / step);
			return BigDecimal.valueOf(origin + index * step);
		}

		private static BigDecimal snapUp(BigDecimal value, double origin, double step) {
			double index = Math.ceil((value.doubleValue() - origin) / step);
			return BigDecimal.valueOf(origin + index * step);
		}
	}
}
