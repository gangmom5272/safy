package com.ojimasam.safy.grid.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.risk.GridObservation;
import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

class GridReportReaderTest {
	private static final Instant WINDOW_END = Instant.parse("2026-08-03T12:00:00Z");
	private static final Instant WINDOW_START = WINDOW_END.minus(Duration.ofMinutes(15));
	private static final String HONGDAE_CODE = "S100-r174-c199";

	private final ReportRepository reports = mock(ReportRepository.class);
	private final RiskAssessmentRepository assessments = mock(RiskAssessmentRepository.class);
	private final GridCellRepository gridCells = mock(GridCellRepository.class);
	private final GridReportReader reader = new GridReportReader(reports, assessments, gridCells);

	@Test
	@DisplayName("같은 셀의 제보를 하나로 묶는다")
	void groupsReportsByCell() {
		givenReports(report(1L, 100L, HONGDAE_CODE), report(2L, 200L, HONGDAE_CODE));
		givenCells(cell(7L, HONGDAE_CODE));
		givenAssessments(assessment(1L, 75.0, 0.86), assessment(2L, 40.0, 0.70));

		Map<Long, List<GridObservation>> byCell = reader.observationsByGridCell(WINDOW_START, WINDOW_END);

		assertThat(byCell).containsOnlyKeys(7L);
		assertThat(byCell.get(7L)).containsExactlyInAnyOrder(
				new GridObservation(1L, 100L, 75.0, 0.86),
				new GridObservation(2L, 200L, 40.0, 0.70)
		);
	}

	@Test
	@DisplayName("다른 셀의 제보는 따로 담는다")
	void separatesDifferentCells() {
		givenReports(report(1L, 100L, HONGDAE_CODE), report(2L, 200L, "S100-r184-c246"));
		givenCells(cell(7L, HONGDAE_CODE), cell(8L, "S100-r184-c246"));
		givenAssessments(assessment(1L, 75.0, 0.86), assessment(2L, 30.0, 0.60));

		Map<Long, List<GridObservation>> byCell = reader.observationsByGridCell(WINDOW_START, WINDOW_END);

		assertThat(byCell).containsOnlyKeys(7L, 8L);
		assertThat(byCell.get(7L)).hasSize(1);
		assertThat(byCell.get(8L)).hasSize(1);
	}

	@Test
	@DisplayName("위험도가 아직 평가되지 않은 제보도 담는다")
	void keepsReportWithoutAssessment() {
		givenReports(report(1L, 100L, HONGDAE_CODE));
		givenCells(cell(7L, HONGDAE_CODE));
		givenAssessments();

		Map<Long, List<GridObservation>> byCell = reader.observationsByGridCell(WINDOW_START, WINDOW_END);

		// finalRisk 가 null 로 들어가고 GridRiskAssessor 가 계산에서 제외한다.
		// 여기서 버리면 "제보는 있었지만 분석하지 못했다"는 사실이 사라진다.
		assertThat(byCell.get(7L)).containsExactly(new GridObservation(1L, 100L, null, 0.0));
	}

	@Test
	@DisplayName("위험도 점수가 null 인 평가도 담는다")
	void keepsAssessmentWithoutScore() {
		givenReports(report(1L, 100L, HONGDAE_CODE));
		givenCells(cell(7L, HONGDAE_CODE));
		givenAssessments(assessment(1L, null, 0.0));

		assertThat(reader.observationsByGridCell(WINDOW_START, WINDOW_END).get(7L))
				.containsExactly(new GridObservation(1L, 100L, null, 0.0));
	}

	@Test
	@DisplayName("셀 행이 없는 제보는 건너뛴다")
	void skipsReportWithoutCellRow() {
		givenReports(report(1L, 100L, HONGDAE_CODE));
		givenCells();
		givenAssessments(assessment(1L, 75.0, 0.86));

		assertThat(reader.observationsByGridCell(WINDOW_START, WINDOW_END)).isEmpty();
	}

	@Test
	@DisplayName("창 안에 제보가 없으면 더 조회하지 않는다")
	void skipsFurtherQueriesWhenNoReport() {
		givenReports();

		assertThat(reader.observationsByGridCell(WINDOW_START, WINDOW_END)).isEmpty();
		verify(assessments, never()).findAllByReportIdIn(ArgumentMatchers.any());
		verify(gridCells, never()).findAllByGridCodeIn(ArgumentMatchers.any());
	}

	private void givenReports(Report... values) {
		when(reports.findByGridCellCodeIsNotNullAndReportedAtBetween(WINDOW_START, WINDOW_END))
				.thenReturn(List.of(values));
	}

	private void givenAssessments(RiskAssessment... values) {
		when(assessments.findAllByReportIdIn(ArgumentMatchers.any())).thenReturn(List.of(values));
	}

	private void givenCells(GridCell... values) {
		when(gridCells.findAllByGridCodeIn(ArgumentMatchers.any())).thenReturn(List.of(values));
	}

	private static Report report(Long id, Long reporterId, String gridCellCode) {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(id);
		when(report.getReporterId()).thenReturn(reporterId);
		when(report.getGridCellCode()).thenReturn(gridCellCode);
		return report;
	}

	private static RiskAssessment assessment(Long reportId, Double finalRisk, double confidence) {
		RiskAssessment assessment = mock(RiskAssessment.class);
		when(assessment.getReportId()).thenReturn(reportId);
		when(assessment.getFinalRiskScore()).thenReturn(finalRisk);
		when(assessment.getRiskConfidence()).thenReturn(confidence);
		return assessment;
	}

	private static GridCell cell(Long id, String gridCode) {
		GridCell cell = mock(GridCell.class);
		when(cell.getId()).thenReturn(id);
		when(cell.getGridCode()).thenReturn(gridCode);
		return cell;
	}
}
