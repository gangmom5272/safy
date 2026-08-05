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
 * 실시간 혼잡도 조회. 자치구(여러 장소 중 최고 혼잡 1곳)와 장소 하나(그 장소 자신) 양쪽에서 쓴다.
 *
 * <p>자치구는 구 안 장소들의 인구를 합산하지 않는다. 121장소는 서로 겹치는 곳이 있어
 * 합산하면 같은 인파를 두 번 세고, 121장소가 덮는 영역의 합일 뿐인데 구 전체 인구처럼
 * 읽힌다. 대신 가장 혼잡한 장소를 그대로 보여줘서 시민이 어디를 피해야 하는지 알 수 있게 한다.
 *
 * <p>장소를 직접 검색한 경우는 이 규칙을 적용하지 않는다 — 후보가 그 장소 하나뿐이라
 * {@link #atPlace} 는 항상 검색한 장소 자신의 값을 돌려준다. 자치구 조회로 대신하면
 * "역삼역을 검색했는데 더 혼잡한 강남역 값이 나온다" 같은 문제가 생긴다.
 */
@Component
public class AreaCongestionReader {
	/**
	 * 이 시간 안에 <b>수집한</b> 값만 쓴다. {@code findLatestPerPlace} 는 시각과 무관하게
	 * 장소별 최신 1건을 주므로, 수집이 멈추면 몇 시간 전 관측이 계속 '실시간'으로 잡힌다.
	 * 수집 주기가 3분이라 15분이면 5회 연속 실패까지 견딘다.
	 *
	 * <p>기준은 {@code collectedAt} 이다. 서울시 {@code observedAt}(PPLTN_TIME)은 수집
	 * 시각보다 28~31분 뒤쳐져서, 그것으로 자르면 정상 수집 중에도 혼잡도가 항상 비어
	 * 있다(운영 실측 24시간 288배치에서 15분 창 통과 0건).
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
		return peakAmong(areaPlaceIndex.placeIdsOf(areaCode));
	}

	/**
	 * 장소 하나의 실시간 혼잡도. 후보 집합이 그 장소 하나뿐이라 {@code peakAmong} 의
	 * 최댓값 선택은 항상 자기 자신을 돌려준다 — 다른 장소와 비교하지 않는다.
	 */
	@Transactional(readOnly = true)
	public Optional<CongestionObservationView> atPlace(Long placeId) {
		return peakAmong(Set.of(placeId));
	}

	private Optional<CongestionObservationView> peakAmong(Set<Long> placeIds) {
		if (placeIds.isEmpty()) {
			return Optional.empty();
		}

		Instant freshSince = clock.instant().minus(FRESH_WINDOW);
		return observationRepository.findLatestPerPlace().stream()
				.filter(observation -> placeIds.contains(observation.getPlace().getId()))
				.filter(observation -> !observation.getCollectedAt().isBefore(freshSince))
				.max(BY_CONGESTION)
				.map(CongestionObservationView::from);
	}
}
