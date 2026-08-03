package com.ojimasam.safy.grid.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.grid.application.GridRiskAssessmentService;

/**
 * 격자 위험도를 주기적으로 다시 계산한다.
 *
 * <p>기본 3분은 121곳 혼잡도 수집 주기와 같다. 새 관측이 들어올 때마다 한 번 계산하는
 * 셈이고, 더 자주 돌려도 공공데이터 쪽 입력은 바뀌지 않는다. 제보는 최대 3분 늦게
 * 반영되는데, 담당자에게 가는 제보 알림은 SSE 로 이미 즉시 전달되고 격자는 배경
 * 위험도라 이 지연을 감수한다.
 *
 * <p>{@code fixedDelay} 라서 이전 회차가 끝난 뒤에 다음 회차를 센다. 첫 실행은 121곳이
 * 덮는 6,782셀을 모두 저장해 오래 걸릴 수 있어 겹침을 막아야 한다.
 */
@Component
public class GridRiskScheduler {
	private static final Logger log = LoggerFactory.getLogger(GridRiskScheduler.class);

	private final GridRiskAssessmentService gridRiskAssessmentService;

	public GridRiskScheduler(GridRiskAssessmentService gridRiskAssessmentService) {
		this.gridRiskAssessmentService = gridRiskAssessmentService;
	}

	@Scheduled(fixedDelayString = "${app.grid-risk.scheduler-delay:PT3M}")
	public void refresh() {
		try {
			gridRiskAssessmentService.refresh();
		} catch (Exception exception) {
			// 한 회차가 실패해도 스케줄러는 살아 있어야 한다. 다음 주기에 다시 계산한다.
			log.error("격자 위험도 계산에 실패했습니다.", exception);
		}
	}
}
