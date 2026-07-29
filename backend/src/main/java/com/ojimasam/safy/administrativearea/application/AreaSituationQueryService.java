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
import com.ojimasam.safy.report.application.ReportFeedQueryService;
import com.ojimasam.safy.administrativearea.application.dto.AreaMapResponse;
import com.ojimasam.safy.administrativearea.application.dto.AreaStatusResponse;

@Service
@Transactional(readOnly = true)
public class AreaSituationQueryService {
	private final AdministrativeAreaRepository administrativeAreaRepository;
	private final ReportFeedQueryService reportFeedQueryService;
	private final Clock clock;

	@Autowired
	public AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService
	) {
		this(administrativeAreaRepository, reportFeedQueryService, Clock.systemUTC());
	}

	AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService,
			Clock clock
	) {
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.reportFeedQueryService = reportFeedQueryService;
		this.clock = clock;
	}

	public AreaStatusResponse status(Long areaId) {
		AdministrativeArea area = findActiveArea(areaId);
		long activeCount = reportFeedQueryService.countActiveReports(areaId);
		long liveCount = reportFeedQueryService.countActiveLiveReports(areaId);
		return AreaStatusResponse.of(area, riskLevel(activeCount, liveCount), activeCount, liveCount, clock.instant());
	}

	public AreaMapResponse map(Long areaId, Instant cursor, int size) {
		AdministrativeArea area = findActiveArea(areaId);
		return new AreaMapResponse(
				status(areaId),
				area.getBoundaryGeoJson(),
				reportFeedQueryService.liveByArea(areaId, cursor, size)
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
