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
	private final AreaCongestionReader areaCongestionReader;
	private final Clock clock;

	@Autowired
	public AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService,
			PublicReportQueryService publicReportQueryService,
			AreaCongestionReader areaCongestionReader
	) {
		this(administrativeAreaRepository, reportFeedQueryService, publicReportQueryService,
				areaCongestionReader, Clock.systemUTC());
	}

	AreaSituationQueryService(
			AdministrativeAreaRepository administrativeAreaRepository,
			ReportFeedQueryService reportFeedQueryService,
			PublicReportQueryService publicReportQueryService,
			AreaCongestionReader areaCongestionReader,
			Clock clock
	) {
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.reportFeedQueryService = reportFeedQueryService;
		this.publicReportQueryService = publicReportQueryService;
		this.areaCongestionReader = areaCongestionReader;
		this.clock = clock;
	}

	/**
	 * 혼잡도는 서울시 실시간 도시데이터에서, '최근 제보' 수는 지도 마커와 같은 공개 피드에서
	 * 온다. 근거가 다른 두 값이라 각각 조회한다.
	 *
	 * <p>예전에는 미종료 제보 건수로 지역 위험도를 만들어 함께 내려보냈다. 제보가 몇 건
	 * 들어왔는지는 현장이 얼마나 위험한지와 다른 값이라 걷어냈다.
	 */
	public AreaStatusResponse status(Long areaId) {
		AdministrativeArea area = findActiveArea(areaId);
		return AreaStatusResponse.of(
				area,
				areaCongestionReader.peakOf(area.getCode()).orElse(null),
				publicReportQueryService.countRecentByArea(areaId),
				clock.instant()
		);
	}

	/** 지도 데이터는 시민 화면이 쓰므로 제보 목록은 공개된 건만 담는다. */
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
}
