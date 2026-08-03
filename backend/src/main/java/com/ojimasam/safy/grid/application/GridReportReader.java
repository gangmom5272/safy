package com.ojimasam.safy.grid.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.risk.GridObservation;
import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

/**
 * 격자 셀별 제보 관측. 격자 위험도 계산의 두 입력 중 하나다.
 *
 * <p>{@code reports} 와 {@code risk_assessments} 를 조인하지 않고 두 번 읽어 자바에서
 * 묶는다. 두 엔티티가 JPA 연관관계 없이 {@code report_id} 로만 이어져 있어 JPQL 조인이
 * 되지 않고, 15분 창 안의 제보 수는 많지 않다.
 *
 * <p>제보는 {@code grid_cell_code} 를 갖고 격자 위험도는 {@code grid_cell_id} 를 쓰므로
 * 여기서 코드를 ID 로 바꾼다. 셀 행은 제보 등록 때 이미 만들어져 있다
 * ({@code GridCellRegistry}).
 */
@Component
public class GridReportReader {
	private final ReportRepository reportRepository;
	private final RiskAssessmentRepository riskAssessmentRepository;
	private final GridCellRepository gridCellRepository;

	public GridReportReader(
			ReportRepository reportRepository,
			RiskAssessmentRepository riskAssessmentRepository,
			GridCellRepository gridCellRepository
	) {
		this.reportRepository = reportRepository;
		this.riskAssessmentRepository = riskAssessmentRepository;
		this.gridCellRepository = gridCellRepository;
	}

	/**
	 * 시간 창 안 제보를 셀별로 묶는다. 제보가 없는 셀은 키가 없다.
	 *
	 * <p>위험도가 아직 평가되지 않은 제보도 담는다. {@code finalRisk} 가 null 로 들어가고
	 * {@code GridRiskAssessor} 가 계산에서 제외한다 — 여기서 버리면 "제보는 있었지만
	 * 분석하지 못했다"는 사실이 사라진다.
	 */
	@Transactional(readOnly = true)
	public Map<Long, List<GridObservation>> observationsByGridCell(Instant windowStart, Instant windowEnd) {
		List<Report> reports =
				reportRepository.findByGridCellCodeIsNotNullAndReportedAtBetween(windowStart, windowEnd);
		if (reports.isEmpty()) {
			return Map.of();
		}

		Map<Long, RiskAssessment> assessmentsByReport = assessmentsByReportId(reports);
		Map<String, Long> cellIdsByCode = cellIdsByCode(reports);

		Map<Long, List<GridObservation>> observationsByCell = new HashMap<>();
		for (Report report : reports) {
			Long gridCellId = cellIdsByCode.get(report.getGridCellCode());
			if (gridCellId == null) {
				// 셀 행이 없으면 격자 위험도를 붙일 자리가 없다. 제보 등록 시 만들어지므로
				// 여기 걸리는 것은 시드·백필로 코드만 채운 오래된 제보다.
				continue;
			}

			RiskAssessment assessment = assessmentsByReport.get(report.getId());
			observationsByCell
					.computeIfAbsent(gridCellId, key -> new ArrayList<>())
					.add(new GridObservation(
							report.getId(),
							report.getReporterId(),
							assessment == null ? null : assessment.getFinalRiskScore(),
							assessment == null ? 0.0 : assessment.getRiskConfidence()
					));
		}

		return observationsByCell;
	}

	private Map<Long, RiskAssessment> assessmentsByReportId(List<Report> reports) {
		List<Long> reportIds = reports.stream().map(Report::getId).toList();

		Map<Long, RiskAssessment> byReportId = new HashMap<>();
		for (RiskAssessment assessment : riskAssessmentRepository.findAllByReportIdIn(reportIds)) {
			byReportId.put(assessment.getReportId(), assessment);
		}
		return byReportId;
	}

	private Map<String, Long> cellIdsByCode(List<Report> reports) {
		Set<String> codes = new HashSet<>();
		reports.forEach(report -> codes.add(report.getGridCellCode()));

		Map<String, Long> idsByCode = new HashMap<>();
		for (GridCell cell : gridCellRepository.findAllByGridCodeIn(codes)) {
			idsByCode.put(cell.getGridCode(), cell.getId());
		}
		return idsByCode;
	}
}
