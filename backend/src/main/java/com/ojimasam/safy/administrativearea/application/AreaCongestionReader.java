package com.ojimasam.safy.administrativearea.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

/**
 * 자치구의 실시간 혼잡도. 구 안 장소들의 최신 관측 중 가장 혼잡한 하나를 고른다.
 *
 * <p>구 안 장소들의 인구를 합산하지 않는다. 121장소는 서로 겹치는 곳이 있어 합산하면 같은
 * 인파를 두 번 세고, 121장소가 덮는 영역의 합일 뿐인데 구 전체 인구처럼 읽힌다. 대신 가장
 * 혼잡한 장소를 그대로 보여줘서 시민이 어디를 피해야 하는지 알 수 있게 한다.
 */
@Component
public class AreaCongestionReader {
	/**
	 * 이 시간 안에 관측된 값만 쓴다. {@code findLatestPerPlace} 는 시각과 무관하게 장소별 최신
	 * 1건을 주므로, 수집이 멈추면 몇 시간 전 관측이 계속 '실시간'으로 잡힌다. 수집 주기가
	 * 3분이라 15분이면 5회 연속 실패까지 견딘다(격자 위험도의 창과 같은 값이다).
	 */
	static final Duration FRESH_WINDOW = Duration.ofMinutes(15);

	/**
	 * 혼잡 등급이 먼저다. enum 선언 순서가 곧 혼잡 강도다
	 * (RELAXED &lt; NORMAL &lt; SLIGHTLY_CROWDED &lt; CROWDED).
	 * 같은 등급이면 추정 인원 상한이 큰 장소를 고른다.
	 */
	private static final Comparator<CityCongestionObservation> BY_CONGESTION =
			Comparator.comparing(CityCongestionObservation::getCongestionLevel)
					.thenComparing(
							CityCongestionObservation::getPopulationMax,
							Comparator.nullsFirst(Comparator.naturalOrder())
					);

	private final CityCongestionObservationRepository observationRepository;
	private final AreaPlaceIndex areaPlaceIndex;
	private final Clock clock;

	@Autowired
	public AreaCongestionReader(
			CityCongestionObservationRepository observationRepository,
			AreaPlaceIndex areaPlaceIndex
	) {
		this(observationRepository, areaPlaceIndex, Clock.systemUTC());
	}

	AreaCongestionReader(
			CityCongestionObservationRepository observationRepository,
			AreaPlaceIndex areaPlaceIndex,
			Clock clock
	) {
		this.observationRepository = observationRepository;
		this.areaPlaceIndex = areaPlaceIndex;
		this.clock = clock;
	}

	/** 근거가 없으면 빈 값이다. 121장소가 없는 자치구, 수집이 멈춘 경우 모두 여기로 온다. */
	@Transactional(readOnly = true)
	public Optional<CongestionObservationView> peakOf(String areaCode) {
		Set<Long> placeIds = areaPlaceIndex.placeIdsOf(areaCode);
		if (placeIds.isEmpty()) {
			return Optional.empty();
		}

		Instant freshSince = clock.instant().minus(FRESH_WINDOW);
		return observationRepository.findLatestPerPlace().stream()
				.filter(observation -> placeIds.contains(observation.getPlace().getId()))
				.filter(observation -> !observation.getObservedAt().isBefore(freshSince))
				.max(BY_CONGESTION)
				.map(CongestionObservationView::from);
	}
}
