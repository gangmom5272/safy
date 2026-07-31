package com.ojimasam.safy.administrativearea.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.application.dto.ReportCursorResponse;
import com.ojimasam.safy.report.application.PublicReportQueryService;
import com.ojimasam.safy.report.application.ReportFeedQueryService;
import com.ojimasam.safy.administrativearea.application.dto.AreaMapResponse;
import com.ojimasam.safy.administrativearea.application.dto.AreaStatusResponse;

@Service
@Transactional(readOnly = true)
public class AreaSituationQueryService {
	private final AdministrativeAreaRepository administrativeAreaRepository;
	private final ReportFeedQueryService reportFeedQueryService;
	private final PublicReportQueryService publicReportQueryService;
	private final Clock clock;

	@Autowired
	public AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService,
			PublicReportQueryService publicReportQueryService
	) {
		this(administrativeAreaRepository, reportFeedQueryService, publicReportQueryService, Clock.systemUTC());
	}

	AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService,
			PublicReportQueryService publicReportQueryService,
			Clock clock
	) {
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.reportFeedQueryService = reportFeedQueryService;
		this.publicReportQueryService = publicReportQueryService;
		this.clock = clock;
	}

	public AreaStatusResponse status(Long areaId) {
		AdministrativeArea area = findActiveArea(areaId);
		long activeCount = reportFeedQueryService.countActiveReports(areaId);
		long liveCount = reportFeedQueryService.countActiveLiveReports(areaId);
		return AreaStatusResponse.of(area, riskLevel(activeCount, liveCount), activeCount, liveCount, clock.instant());
	}

	/**
	 * 지도 데이터는 시민 화면이 쓰므로 제보 목록은 공개된 건만 담는다.
	 * 위험도 요약(status)은 미공개 제보까지 세는 집계라 그대로 둔다.
	 */
	public AreaMapResponse map(Long areaId, Instant cursor, int size) {
		AdministrativeArea area = findActiveArea(areaId);
		return new AreaMapResponse(
				status(areaId),
				area.getBoundaryGeoJson(),
				publicReportQueryService.reportsByArea(areaId, cursor, size)
		);
	}

	public ReportCursorResponse liveReports(Long areaId, Instant cursor, int size) {
		findActiveArea(areaId);
		return reportFeedQueryService.liveByArea(areaId, cursor, size);
	}

	private AdministrativeArea findActiveArea(Long areaId) {
		return administrativeAreaRepository.findByIdAndStatus(areaId, AdministrativeAreaStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	private String riskLevel(long activeCount, long liveCount) {
		long score = activeCount + (liveCount * 2);
		if (score >= 10) return "HIGH";
		if (score >= 3) return "MEDIUM";
		return "LOW";
	}
}
