package com.ojimasam.safy.place.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.application.AreaCongestionReader;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.place.application.dto.PlaceStatusResponse;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.report.application.PlaceRecentReportCounter;

/**
 * 121장소를 직접 검색했을 때의 상황 요약. 자치구를 거치지 않고 그 장소 자신의 값만 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class PlaceSituationQueryService {
	private final PlaceRepository placeRepository;
	private final AreaCongestionReader areaCongestionReader;
	private final PlaceRecentReportCounter placeRecentReportCounter;
	private final Clock clock;

	@Autowired
	public PlaceSituationQueryService(
			PlaceRepository placeRepository,
			AreaCongestionReader areaCongestionReader,
			PlaceRecentReportCounter placeRecentReportCounter
	) {
		this(placeRepository, areaCongestionReader, placeRecentReportCounter, Clock.systemUTC());
	}

	PlaceSituationQueryService(
			PlaceRepository placeRepository,
			AreaCongestionReader areaCongestionReader,
			PlaceRecentReportCounter placeRecentReportCounter,
			Clock clock
	) {
		this.placeRepository = placeRepository;
		this.areaCongestionReader = areaCongestionReader;
		this.placeRecentReportCounter = placeRecentReportCounter;
		this.clock = clock;
	}

	public PlaceStatusResponse status(Long placeId) {
		Place place = placeRepository.findOneById(placeId)
				.filter(Place::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

		return PlaceStatusResponse.of(
				place,
				areaCongestionReader.atPlace(placeId).orElse(null),
				placeRecentReportCounter.countWithin(placeId),
				clock.instant()
		);
	}
}
