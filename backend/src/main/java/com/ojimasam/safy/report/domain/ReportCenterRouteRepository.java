package com.ojimasam.safy.report.domain;

import java.util.Optional;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;

public interface ReportCenterRouteRepository {
	ReportCenterRoute save(ReportCenterRoute route);

	/**
	 * 대기 중인 변경을 즉시 DB 에 반영한다. 이관은 옛 라우트를 REROUTED 로 내리고 새 ACTIVE
	 * 라우트를 넣는데, 부분 유니크 인덱스가 제보당 ACTIVE 라우트를 하나로 제한하므로
	 * 그 사이에 flush 가 필요하다(아래 save 주석 참고).
	 */
	void flush();

	long countByControlCenter_IdAndStatus(Long controlCenterId, ReportCenterRouteStatus status);

	Optional<ReportCenterRoute> findByReport_IdAndStatus(Long reportId, ReportCenterRouteStatus status);

	Optional<ReportCenterRoute> findTopByReport_IdOrderByRoutedAtDesc(Long reportId);

	/**
	 * 담당 이력 타임라인용 라우팅 전체. 자동 이관(FAILOVER)은 제보 상태가 그대로라
	 * 상태 이력에도 배정에도 남지 않으므로, 여기서만 드러난다.
	 */
	java.util.List<ReportCenterRoute> findAllByReport_IdOrderByRoutedAtAscIdAsc(Long reportId);
}
