package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.PlaceBoundary;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;

/**
 * 장소 검색의 '최근 제보' 수. 검색한 장소의 실제 경계 폴리곤 안에 있는 제보만 센다.
 *
 * <p>제보에 이미 저장된 {@code placeId}(가장 작은 포함 장소 하나)를 그대로 세지 않는다.
 * 홍대입구역처럼 더 큰 장소(홍대 관광특구) 안에 있는 작은 장소는 겹치면 항상 작은 쪽에
 * 배정되므로({@code PlaceLocator}), 관광특구·공원처럼 넓은 장소를 검색하면 실제로는
 * 폴리곤 안인데도 다른(더 작은) 장소에 배정된 제보가 빠져 과소 집계된다. 그래서 매번
 * 좌표를 검색한 장소의 폴리곤으로 직접 재판정한다.
 */
@Component
public class PlaceRecentReportCounter {
	private final ReportRepository reportRepository;
	private final PlaceBoundaries placeBoundaries;
	private final Clock clock;

	@Autowired
	public PlaceRecentReportCounter(ReportRepository reportRepository, PlaceBoundaries placeBoundaries) {
		this(reportRepository, placeBoundaries, Clock.systemUTC());
	}

	PlaceRecentReportCounter(ReportRepository reportRepository, PlaceBoundaries placeBoundaries, Clock clock) {
		this.reportRepository = reportRepository;
		this.placeBoundaries = placeBoundaries;
		this.clock = clock;
	}

	/** 경계를 읽을 수 없는 장소(예: 좌표 미확정)면 0이다. */
	@Transactional(readOnly = true)
	public long countWithin(Long placeId) {
		PlaceBoundary boundary = placeBoundaries.all().stream()
				.filter(candidate -> candidate.placeId().equals(placeId))
				.findFirst()
				.orElse(null);
		if (boundary == null) {
			return 0;
		}

		Instant since = clock.instant().minus(PublicReportQueryService.RECENT_WINDOW);
		return reportRepository
				.findByPublicVisibilityAndReportedAtAfter(ReportPublicVisibility.PUBLISHED, since)
				.stream()
				.filter(report -> report.getLatitude() != null && report.getLongitude() != null)
				.filter(report -> boundary.contains(report.getLongitude().doubleValue(), report.getLatitude().doubleValue()))
				.count();
	}
}
