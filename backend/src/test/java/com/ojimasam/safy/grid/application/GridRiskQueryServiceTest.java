package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.analysis.domain.risk.GridObservationStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.analysis.domain.risk.GridRiskSnapshot;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.grid.application.dto.GridRiskResponse;
import com.ojimasam.safy.grid.application.dto.GridRiskTrendPoint;
import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.grid.domain.GridRiskHistory;
import com.ojimasam.safy.grid.domain.GridRiskHistoryRepository;

class GridRiskQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
	private static final String HONGDAE_CODE = "S100-r174-c199";

	private final GridCellRepository gridCells = mock(GridCellRepository.class);
	private final GridRiskHistoryRepository histories = mock(GridRiskHistoryRepository.class);
	private final GridRiskQueryService service = new GridRiskQueryService(
			gridCells, histories, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	@DisplayName("격자 한 칸의 위험도를 그릴 사각형 경계와 함께 준다")
	void returnsRiskWithCellBounds() {
		givenCellsInBounds(cell(7L, 174, 199));
		givenLatest(history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		List<GridRiskResponse> risks = risks(1);

		assertThat(risks).hasSize(1);
		GridRiskResponse risk = risks.get(0);
		assertThat(risk.gridCode()).isEqualTo(HONGDAE_CODE);
		assertThat(risk.resolutionMeters()).isEqualTo(100);
		// 프론트가 코드로 역산하지 않아도 되도록 경계를 담는다.
		assertThat(risk.southWestLatitude()).isEqualByComparingTo("37.5566000");
		assertThat(risk.northEastLatitude()).isEqualByComparingTo("37.5575000");
		assertThat(risk.score()).isEqualTo(60.0);
		assertThat(risk.congestionLevel()).isEqualTo(RiskLevel.MEDIUM);
	}

	@Test
	@DisplayName("resolution 으로 묶으면 최댓값 칸이 대표가 된다")
	void mergesToPeakCell() {
		// 같은 800m 칸에 든 세 칸. resolution 8 이면 row 174·175 → 21, column 198·199 → 24 다.
		givenCellsInBounds(cell(7L, 174, 199), cell(8L, 175, 199), cell(9L, 174, 198));
		givenLatest(
				history(7L, 30.0, RiskLevel.LOW, GridObservationStatus.PUBLIC_DATA),
				history(8L, 88.0, RiskLevel.VERY_HIGH, GridObservationStatus.REPORTED),
				history(9L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		List<GridRiskResponse> risks = risks(8);

		// 세 칸이 한 칸으로 묶인다.
		assertThat(risks).hasSize(1);
		GridRiskResponse merged = risks.get(0);
		assertThat(merged.score()).isEqualTo(88.0);
		assertThat(merged.congestionLevel()).isEqualTo(RiskLevel.VERY_HIGH);
		assertThat(merged.observationStatus()).isEqualTo(GridObservationStatus.REPORTED);
		assertThat(merged.resolutionMeters()).isEqualTo(800);
		// 클릭하면 그 100m 칸의 추이를 볼 수 있어야 한다.
		assertThat(merged.gridCode()).isEqualTo("S100-r175-c199");
	}

	@Test
	@DisplayName("서로 다른 묶음 칸은 각각 남는다")
	void keepsSeparateMergedCells() {
		// row 174 → 21, row 200 → 25 (resolution 8)
		givenCellsInBounds(cell(7L, 174, 199), cell(8L, 200, 199));
		givenLatest(
				history(7L, 30.0, RiskLevel.LOW, GridObservationStatus.PUBLIC_DATA),
				history(8L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		assertThat(risks(8)).hasSize(2);
	}

	@Test
	@DisplayName("resolution 은 1과 16 사이로 조정한다")
	void clampsResolution() {
		givenCellsInBounds(cell(7L, 174, 199));
		givenLatest(history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		assertThat(risks(0).get(0).resolutionMeters()).isEqualTo(100);
		assertThat(risks(999).get(0).resolutionMeters())
				.isEqualTo(100 * GridRiskQueryService.MAX_RESOLUTION);
	}

	@Test
	@DisplayName("뷰포트를 격자 경계로 넓혀 조회한다")
	void snapsBoundsToGridEdges() {
		givenCellsInBounds(cell(7L, 174, 199));
		givenLatest(history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		service.risksInBounds(
				decimal("37.55661"), decimal("37.55749"),
				decimal("126.92431"), decimal("126.92543"), 1);

		ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(gridCells).findAllByCenterLatitudeBetweenAndCenterLongitudeBetween(
				captor.capture(), captor.capture(), captor.capture(), captor.capture());

		// 화면에 반쯤 걸친 칸도 그려야 하므로 바깥으로 넓힌다.
		List<BigDecimal> snapped = captor.getAllValues();
		assertThat(snapped.get(0).doubleValue()).isLessThanOrEqualTo(37.55661);
		assertThat(snapped.get(1).doubleValue()).isGreaterThanOrEqualTo(37.55749);
		assertThat(snapped.get(2).doubleValue()).isLessThanOrEqualTo(126.92431);
		assertThat(snapped.get(3).doubleValue()).isGreaterThanOrEqualTo(126.92543);
	}

	@Test
	@DisplayName("이력이 없는 칸은 응답에서 빠진다")
	void omitsCellsWithoutHistory() {
		givenCellsInBounds(cell(7L, 174, 199), cell(8L, 184, 246));
		// 8번 칸은 아직 아무 근거도 없었다.
		givenLatest(history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA));

		// 위험도 0 으로 채우지 않는다. 화면에서 색칠하지 않아야 한다.
		assertThat(risks(1)).hasSize(1);
	}

	@Test
	@DisplayName("위험이 끝난 칸(NO_DATA)은 응답에서 빠진다")
	void omitsTerminatedCells() {
		givenCellsInBounds(cell(7L, 174, 199), cell(8L, 174, 198));
		givenLatest(
				history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA),
				// 근거가 사라져 종료된 칸이다.
				history(8L, null, null, GridObservationStatus.NO_DATA));

		List<GridRiskResponse> risks = risks(1);

		assertThat(risks).hasSize(1);
		assertThat(risks.get(0).gridCode()).isEqualTo(HONGDAE_CODE);
	}

	@Test
	@DisplayName("종료된 칸이 묶음 안에 있어도 대표가 되지 않는다")
	void terminatedCellNeverBecomesPeak() {
		givenCellsInBounds(cell(7L, 174, 199), cell(8L, 174, 198));
		givenLatest(
				history(7L, 30.0, RiskLevel.LOW, GridObservationStatus.PUBLIC_DATA),
				history(8L, null, null, GridObservationStatus.NO_DATA));

		List<GridRiskResponse> risks = risks(8);

		assertThat(risks).hasSize(1);
		assertThat(risks.get(0).score()).isEqualTo(30.0);
	}

	@Test
	@DisplayName("범위가 뒤집혔거나 같으면 거부한다")
	void rejectsInvalidBounds() {
		assertThatThrownBy(() -> service.risksInBounds(
				decimal("37.60"), decimal("37.55"), decimal("126.92"), decimal("127.00"), 1))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service.risksInBounds(
				decimal("37.55"), decimal("37.55"), decimal("126.92"), decimal("127.00"), 1))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("너무 넓은 범위는 거부한다")
	void rejectsTooWideBounds() {
		// 인증 없이 열린 API 라 상한이 없으면 요청 하나가 전체 스캔이 된다.
		assertThatThrownBy(() -> service.risksInBounds(
				decimal("30.0"), decimal("40.0"), decimal("126.0"), decimal("127.0"), 1))
				.isInstanceOf(BusinessException.class);

		verify(gridCells, never()).findAllByCenterLatitudeBetweenAndCenterLongitudeBetween(
				any(), any(), any(), any());
	}

	@Test
	@DisplayName("뷰포트에 칸이 없으면 이력을 읽지 않는다")
	void skipsHistoryQueryWhenNoCellInBounds() {
		givenCellsInBounds();

		assertThat(risks(1)).isEmpty();
		verify(histories, never()).findLatestByGridCellIdIn(any());
	}

	@Test
	@DisplayName("추이는 조회 기간만큼 거슬러 읽는다")
	void readsTrendWithinLookback() {
		when(gridCells.findByGridCode(HONGDAE_CODE)).thenReturn(Optional.of(cell(7L, 174, 199)));
		when(histories.findAllByGridCellIdAndWindowEndedAtAfterOrderByWindowEndedAtDesc(any(), any()))
				.thenReturn(List.of(
						history(7L, 60.0, RiskLevel.MEDIUM, GridObservationStatus.PUBLIC_DATA),
						history(7L, 30.0, RiskLevel.LOW, GridObservationStatus.PUBLIC_DATA)));

		List<GridRiskTrendPoint> trend = service.trend(HONGDAE_CODE, Duration.ofHours(6));

		assertThat(trend).extracting(GridRiskTrendPoint::score).containsExactly(60.0, 30.0);

		ArgumentCaptor<Instant> afterCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(histories).findAllByGridCellIdAndWindowEndedAtAfterOrderByWindowEndedAtDesc(
				eq(7L), afterCaptor.capture());
		assertThat(afterCaptor.getValue()).isEqualTo(NOW.minus(Duration.ofHours(6)));
	}

	@Test
	@DisplayName("셀 행이 없는 코드의 추이는 빈 목록이다")
	void returnsEmptyTrendForUnknownCell() {
		when(gridCells.findByGridCode("S100-r0-c0")).thenReturn(Optional.empty());

		assertThat(service.trend("S100-r0-c0", Duration.ofHours(6))).isEmpty();
	}

	private List<GridRiskResponse> risks(int resolution) {
		return service.risksInBounds(
				decimal("37.55"), decimal("37.60"), decimal("126.92"), decimal("127.00"), resolution);
	}

	private void givenCellsInBounds(GridCell... cells) {
		when(gridCells.findAllByCenterLatitudeBetweenAndCenterLongitudeBetween(
				any(), any(), any(), any())).thenReturn(List.of(cells));
	}

	private void givenLatest(GridRiskHistory... histories2) {
		when(histories.findLatestByGridCellIdIn(any())).thenReturn(List.of(histories2));
	}

	private static GridCell cell(Long id, int row, int column) {
		GridCell cell = GridCell.of(new GridCellCode(row, column));
		ReflectionTestUtils.setField(cell, "id", id);
		return cell;
	}

	private static GridRiskHistory history(
			Long gridCellId, Double score, RiskLevel level, GridObservationStatus status) {
		return GridRiskHistory.of(
				new GridRiskSnapshot(gridCellId, score, level, status, 1, 1, 0.5,
						"safy-grid-risk-v0.1"),
				NOW.minus(Duration.ofMinutes(15)),
				NOW);
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
