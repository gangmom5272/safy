package com.ojimasam.safy.report.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.geo.Distances;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;

/**
 * 좌표 중심 반경 안의 '최근 제보' 수. 폴리곤이 없는 임의 지점에 쓴다.
 *
 * <p>사각형(bbox)으로 DB 에서 1차로 좁힌 뒤 실제 거리로 2차 필터를 한다. bbox 만 쓰면
 * 모서리 쪽이 반경보다 최대 41% 멀어 원 밖 제보가 섞인다.
 */
@Component
public class RadiusRecentReportCounter {
	/**
	 * 기본 반경. 도보 10~15분권이라 "이 지점 주변"으로 읽을 수 있는 범위다.
	 *
	 * <p>장소 검색({@code PlaceRecentReportCounter})은 폴리곤으로 정확히 세지만, 임의 좌표는
	 * 경계가 없어 반경으로 대신한다. 그래서 화면 각주에 반경을 밝혀야 한다.
	 */
	public static final double DEFAULT_RADIUS_METERS = 1_000;

	private final ReportRepository reportRepository;
	private final Clock clock;

	@Autowired
	public RadiusRecentReportCounter(ReportRepository reportRepository) {
		this(reportRepository, Clock.systemUTC());
	}

	RadiusRecentReportCounter(ReportRepository reportRepository, Clock clock) {
		this.reportRepository = reportRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public long countWithin(BigDecimal latitude, BigDecimal longitude, double radiusMeters) {
		if (latitude == null || longitude == null || radiusMeters <= 0) {
			return 0;
		}

		double centerLatitude = latitude.doubleValue();
		double centerLongitude = longitude.doubleValue();
		double latitudeSpan = Distances.latitudeSpanFor(radiusMeters);
		double longitudeSpan = Distances.longitudeSpanFor(radiusMeters, centerLatitude);
		Instant since = clock.instant().minus(PublicReportQueryService.RECENT_WINDOW);

		return reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
						ReportPublicVisibility.PUBLISHED,
						since,
						BigDecimal.valueOf(centerLatitude - latitudeSpan),
						BigDecimal.valueOf(centerLatitude + latitudeSpan),
						BigDecimal.valueOf(centerLongitude - longitudeSpan),
						BigDecimal.valueOf(centerLongitude + longitudeSpan))
				.stream()
				.filter(report -> report.getLatitude() != null && report.getLongitude() != null)
				.filter(report -> Distances.metersBetween(
						centerLatitude, centerLongitude,
						report.getLatitude().doubleValue(), report.getLongitude().doubleValue()) <= radiusMeters)
				.count();
	}
}
