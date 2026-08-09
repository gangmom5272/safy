package com.ojimasam.safy.publicdata.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.publicdata.application.dto.CulturalEventView;
import com.ojimasam.safy.publicdata.application.dto.PlaceCongestionResponse;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;
import com.ojimasam.safy.publicdata.domain.CulturalEventRepository;

/** 지도에 표시할 공공데이터를 조회한다. */
@Service
@Transactional(readOnly = true)
public class PublicDataQueryService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final CityCongestionObservationRepository observationRepository;
	private final CulturalEventRepository culturalEventRepository;
	private final Clock clock;

	@Autowired
	public PublicDataQueryService(
			CityCongestionObservationRepository observationRepository,
			CulturalEventRepository culturalEventRepository
	) {
		this(observationRepository, culturalEventRepository, Clock.systemUTC());
	}

	PublicDataQueryService(
			CityCongestionObservationRepository observationRepository,
			CulturalEventRepository culturalEventRepository,
			Clock clock
	) {
		this.observationRepository = observationRepository;
		this.culturalEventRepository = culturalEventRepository;
		this.clock = clock;
	}

	public List<PlaceCongestionResponse> latestCongestion() {
		return observationRepository.findLatestPerPlace()
				.stream()
				.map(PlaceCongestionResponse::from)
				.toList();
	}

	/**
	 * 종료된 행사도 테이블에는 남으므로 오늘 날짜로 걸러낸다.
	 * 기간 문자열이 파싱되지 않아 날짜가 비어 있는 행사는 조회되지 않는다.
	 */
	public List<CulturalEventView> ongoingEvents() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
		return culturalEventRepository
				.findAllByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(today, today)
				.stream()
				.map(CulturalEventView::from)
				.toList();
	}
}
