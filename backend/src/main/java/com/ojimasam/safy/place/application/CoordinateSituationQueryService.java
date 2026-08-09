package com.ojimasam.safy.place.application;

import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.place.application.dto.CoordinateStatusResponse;
import com.ojimasam.safy.report.application.RadiusRecentReportCounter;

/**
 * 좌표 하나로 상황 요약을 만든다. 외부 검색으로 찾은 구체적 장소용이다.
 *
 * <p>혼잡도는 그 좌표의 격자를 덮는 121장소(없으면 최근접), 최근 제보는 반경 1km 다.
 * 두 범위가 다르므로 응답에 각각의 근거를 함께 담아 화면이 각주로 밝힐 수 있게 한다.
 */
@Service
@Transactional(readOnly = true)
public class CoordinateSituationQueryService {
	private final CoordinateCongestionReader coordinateCongestionReader;
	private final RadiusRecentReportCounter radiusRecentReportCounter;
	private final Clock clock;

	@Autowired
	public CoordinateSituationQueryService(
			CoordinateCongestionReader coordinateCongestionReader,
			RadiusRecentReportCounter radiusRecentReportCounter
	) {
		this(coordinateCongestionReader, radiusRecentReportCounter, Clock.systemUTC());
	}

	CoordinateSituationQueryService(
			CoordinateCongestionReader coordinateCongestionReader,
			RadiusRecentReportCounter radiusRecentReportCounter,
			Clock clock
	) {
		this.coordinateCongestionReader = coordinateCongestionReader;
		this.radiusRecentReportCounter = radiusRecentReportCounter;
		this.clock = clock;
	}

	public CoordinateStatusResponse status(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "위도와 경도는 필수입니다.");
		}

		double radiusMeters = RadiusRecentReportCounter.DEFAULT_RADIUS_METERS;

		return CoordinateStatusResponse.of(
				coordinateCongestionReader.at(latitude, longitude).orElse(null),
				radiusRecentReportCounter.countWithin(latitude, longitude, radiusMeters),
				(int) radiusMeters,
				clock.instant()
		);
	}
}
