package com.ojimasam.safy.grid.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.analysis.domain.risk.GridObservation;
import com.ojimasam.safy.analysis.domain.risk.GridRiskAssessor;
import com.ojimasam.safy.analysis.domain.risk.GridRiskInput;
import com.ojimasam.safy.analysis.domain.risk.GridRiskSnapshot;
import com.ojimasam.safy.grid.domain.GridRiskHistory;
import com.ojimasam.safy.grid.domain.GridRiskHistoryRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;

/**
 * 격자 위험도를 계산해 이력으로 남긴다.
 *
 * <p>계산 대상은 <b>근거가 있을 수 있는 셀과 직전까지 위험했던 셀</b>이다 — 시간 창 안에
 * 제보가 들어온 셀, 121곳이 덮는 셀, 그리고 마지막 이력이 아직 활성인 셀의 합집합이다.
 * 서울 전역 12만 셀을 훑으면 대부분이 근거 없는 칸이라 계산도 저장도 낭비다.
 *
 * <p>직전 활성 셀을 포함하는 이유가 중요하다. 근거가 만료된 셀을 대상에서 빼면 이력이
 * 갱신되지 않아 <b>마지막 위험도가 지도에 영구히 남는다.</b> 근거가 사라졌으면
 * {@code NO_DATA} 이력을 남겨 위험이 끝났음을 명시한다.
 *
 * <p>저장은 <b>값이 바뀔 때만</b> 한다. 121곳이 덮는 셀 6,782개는 혼잡도가 항상 있어
 * 매 주기 저장하면 하루 325만 행이 되는데, 혼잡도 등급은 3분마다 바뀌지 않아 대부분이
 * 같은 값의 반복이다. 추이는 계단 함수로 재구성되므로 정보가 사라지지 않는다.
 */
@Service
public class GridRiskAssessmentService {
	private static final Logger log = LoggerFactory.getLogger(GridRiskAssessmentService.class);

	private final GridReportReader gridReportReader;
	private final GridCongestionReader gridCongestionReader;
	private final GridRiskHistoryRepository gridRiskHistoryRepository;
	private final Clock clock;

	@Autowired
	public GridRiskAssessmentService(
			GridReportReader gridReportReader,
			GridCongestionReader gridCongestionReader,
			GridRiskHistoryRepository gridRiskHistoryRepository
	) {
		this(gridReportReader, gridCongestionReader, gridRiskHistoryRepository, Clock.systemUTC());
	}

	GridRiskAssessmentService(
			GridReportReader gridReportReader,
			GridCongestionReader gridCongestionReader,
			GridRiskHistoryRepository gridRiskHistoryRepository,
			Clock clock
	) {
		this.gridReportReader = gridReportReader;
		this.gridCongestionReader = gridCongestionReader;
		this.gridRiskHistoryRepository = gridRiskHistoryRepository;
		this.clock = clock;
	}

	/**
	 * {@link GridRiskAssessor#WINDOW_MINUTES} 창으로 계산한다. 값이 바뀐 격자 수를 돌려준다.
	 *
	 * <p>이력에 저장할 창 끝은 분 단위로 자른다. 같은 분에 두 번 돌면 같은 행을 갱신하므로
	 * {@code UNIQUE(grid_cell_id, window_ended_at)} 와 충돌하지 않는다. 제보 조회 상한은
	 * 자르지 않은 현재 시각이다.
	 */
	@Transactional
	public int refresh() {
		Instant now = clock.instant();
		Instant windowEnd = now.truncatedTo(ChronoUnit.MINUTES);
		Instant windowStart = windowEnd.minus(Duration.ofMinutes(GridRiskAssessor.WINDOW_MINUTES));

		// 제보는 자르지 않은 '지금'까지 읽는다. 상한을 windowEnd 로 두면 이번 분에 접수·승인된
		// 제보가 통째로 빠져 다음 회차까지 최대 한 주기를 더 기다린다. 절삭은 저장 키를
		// 안정시키려는 것이지 조회 범위를 좁히려는 것이 아니다.
		Map<Long, List<GridObservation>> reportsByCell =
				gridReportReader.observationsByGridCell(windowStart, now);
		Map<Long, CongestionLevel> congestionByCell =
				gridCongestionReader.latestByGridCell(windowStart);

		Map<Long, GridRiskHistory> latestByCell = latestByCell();

		Set<Long> targets = new HashSet<>(reportsByCell.keySet());
		targets.addAll(congestionByCell.keySet());
		// 근거가 만료된 셀도 다시 계산해야 위험 종료를 기록할 수 있다.
		latestByCell.forEach((gridCellId, latest) -> {
			if (latest.isActive()) {
				targets.add(gridCellId);
			}
		});
		if (targets.isEmpty()) {
			log.debug("격자 위험도 근거가 없어 저장하지 않았습니다.");
			return 0;
		}

		int changed = 0;
		for (Long gridCellId : targets) {
			GridRiskSnapshot snapshot = GridRiskAssessor.assess(new GridRiskInput(
					gridCellId,
					reportsByCell.get(gridCellId),
					congestionByCell.get(gridCellId)
			));

			if (store(snapshot, latestByCell.get(gridCellId), windowStart, windowEnd)) {
				changed++;
			}
		}

		log.info("격자 {}개의 위험도가 바뀌어 저장했습니다. 대상 {}개, 창 {} ~ {}",
				changed, targets.size(), windowStart, windowEnd);
		return changed;
	}

	private Map<Long, GridRiskHistory> latestByCell() {
		Map<Long, GridRiskHistory> latestByCell = new HashMap<>();
		for (GridRiskHistory history : gridRiskHistoryRepository.findLatestPerGridCell()) {
			latestByCell.put(history.getGridCellId(), history);
		}
		return latestByCell;
	}

	/** 저장하거나 갱신했으면 true. 값이 그대로라 건너뛰었으면 false. */
	private boolean store(
			GridRiskSnapshot snapshot, GridRiskHistory latest, Instant windowStart, Instant windowEnd) {
		if (latest == null) {
			// 근거가 처음부터 없는 칸은 저장하지 않는다. 서울 전역 대부분이 그런 칸이고,
			// 조회 시 행이 없으면 NO_DATA 로 본다.
			if (snapshot.score() == null) {
				return false;
			}

			gridRiskHistoryRepository.save(GridRiskHistory.of(snapshot, windowStart, windowEnd));
			return true;
		}

		// 같은 창을 다시 계산한 경우다. 창 도중 제보가 더 들어와 값이 달라질 수 있어
		// 마지막 계산이 이긴다.
		if (latest.getWindowEndedAt().equals(windowEnd)) {
			if (unchanged(snapshot, latest)) {
				return false;
			}
			latest.refresh(snapshot, windowEnd);
			return true;
		}

		if (unchanged(snapshot, latest)) {
			return false;
		}

		gridRiskHistoryRepository.save(GridRiskHistory.of(snapshot, windowStart, windowEnd));
		return true;
	}

	/**
	 * 지도에 보이는 값이 같은지 본다.
	 *
	 * <p>근거 수와 제보자 수는 비교하지 않는다. 같은 점수에서 제보가 한 건 늘어난 것은
	 * 지도 표시를 바꾸지 않고, 그것까지 새 행으로 남기면 값이 바뀔 때만 저장하는 효과가
	 * 사라진다.
	 */
	private static boolean unchanged(GridRiskSnapshot snapshot, GridRiskHistory latest) {
		return Objects.equals(snapshot.score(), latest.getScore())
				&& snapshot.congestionLevel() == latest.getCongestionLevel()
				&& snapshot.observationStatus() == latest.getObservationStatus();
	}
}
