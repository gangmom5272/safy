package com.ojimasam.safy.place.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.geo.Distances;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridCellRepository;
import com.ojimasam.safy.grid.domain.PlaceGridMapping;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.place.application.dto.CoordinateCongestionView;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

/**
 * 임의 좌표의 실시간 혼잡도. 외부 검색으로 찾은 구체적 장소처럼 우리 식별자가 없는
 * 지점에 쓴다.
 *
 * <p><b>격자에 저장된 값을 쓰지 않는다.</b> {@code grid_risk_snapshots} 의
 * {@code congestion_level} 은 이름과 달리 위험 등급(5단계)이고, 서울시 4단계가 점수로
 * 바뀐 뒤 재분류되면서 '약간 붐빔'(45)과 '붐빔'(60)이 같은 {@code medium} 으로 뭉개진다.
 * 게다가 제보 위험도와 최댓값 비교를 거쳐 인구 밀집과 무관하게 등급이 오를 수 있다.
 * 그래서 격자는 <b>어느 장소를 볼지 판정하는 수단</b>으로만 쓰고, 값은 그 장소의 원본
 * 관측에서 가져온다 — 자치구·121장소 검색과 척도가 같아진다.
 *
 * <p>찾는 순서는 격자 → 최근접이다. 격자는 100m 단위라 장소 경계에 살짝 걸친 칸도 잡히는데,
 * 이 용도에서 100m 오차는 충분하고 기준 장소명을 각주로 밝히므로 사용자가 확인할 수 있다.
 */
@Component
public class CoordinateCongestionReader {
	/**
	 * 최근접 장소를 이 거리까지만 인정한다. 넘으면 '정보 없음'이다.
	 *
	 * <p>제한이 없으면 서울 외곽 좌표에 한참 떨어진 장소를 근거로 들이대게 된다
	 * ("OO에서 가까운 강남역 기준"). 그건 정보가 아니라 오해다.
	 */
	static final double NEAREST_PLACE_LIMIT_METERS = 2_000;

	/** {@code AreaCongestionReader.FRESH_WINDOW} 와 같은 기준이다(수집 시각 기반 15분). */
	static final Duration FRESH_WINDOW = Duration.ofMinutes(15);

	/**
	 * 한 격자를 여러 장소가 덮으면 가장 혼잡한 곳을 쓴다. 격자 위험도가 같은 상황에서
	 * 최고 등급을 쓰는 것과 같은 규칙이다(격자 명세 6.2).
	 */
	private static final Comparator<CityCongestionObservation> BY_CONGESTION =
			Comparator.comparing(CityCongestionObservation::getCongestionLevel)
					.thenComparing(
							CityCongestionObservation::getPopulationMax,
							Comparator.nullsFirst(Comparator.naturalOrder())
					);

	private final CityCongestionObservationRepository observationRepository;
	private final PlaceGridMappingRepository placeGridMappingRepository;
	private final GridCellRepository gridCellRepository;
	private final PlaceRepository placeRepository;
	private final Clock clock;

	@Autowired
	public CoordinateCongestionReader(
			CityCongestionObservationRepository observationRepository,
			PlaceGridMappingRepository placeGridMappingRepository,
			GridCellRepository gridCellRepository,
			PlaceRepository placeRepository
	) {
		this(observationRepository, placeGridMappingRepository, gridCellRepository, placeRepository,
				Clock.systemUTC());
	}

	CoordinateCongestionReader(
			CityCongestionObservationRepository observationRepository,
			PlaceGridMappingRepository placeGridMappingRepository,
			GridCellRepository gridCellRepository,
			PlaceRepository placeRepository,
			Clock clock
	) {
		this.observationRepository = observationRepository;
		this.placeGridMappingRepository = placeGridMappingRepository;
		this.gridCellRepository = gridCellRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<CoordinateCongestionView> at(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			return Optional.empty();
		}

		// 신선한 관측이 없으면 어느 경로로 찾아도 보여줄 값이 없다. 먼저 한 번만 읽는다.
		Instant freshSince = clock.instant().minus(FRESH_WINDOW);
		var freshObservations = observationRepository.findLatestPerPlace().stream()
				.filter(observation -> !observation.getCollectedAt().isBefore(freshSince))
				.toList();
		if (freshObservations.isEmpty()) {
			return Optional.empty();
		}

		Set<Long> coveringPlaceIds = placeIdsCovering(latitude, longitude);
		Optional<CongestionObservationView> byGrid = freshObservations.stream()
				.filter(observation -> coveringPlaceIds.contains(observation.getPlace().getId()))
				.max(BY_CONGESTION)
				.map(CongestionObservationView::from);
		if (byGrid.isPresent()) {
			return byGrid.map(CoordinateCongestionView::grid);
		}

		return nearestPlaceId(latitude, longitude)
				.flatMap(placeId -> freshObservations.stream()
						.filter(observation -> observation.getPlace().getId().equals(placeId))
						.findFirst()
						.map(CongestionObservationView::from))
				.map(CoordinateCongestionView::nearest);
	}

	/** 좌표가 속한 격자 셀을 덮는 장소들. 셀 행이 없으면(아무 일도 없던 칸) 빈 집합이다. */
	private Set<Long> placeIdsCovering(BigDecimal latitude, BigDecimal longitude) {
		String gridCode = GridCellCode.of(latitude, longitude).value();

		return gridCellRepository.findByGridCode(gridCode)
				.map(cell -> {
					Set<Long> placeIds = new HashSet<>();
					for (PlaceGridMapping mapping : placeGridMappingRepository.findAll()) {
						if (mapping.getGridCellId().equals(cell.getId())) {
							placeIds.add(mapping.getPlaceId());
						}
					}
					return placeIds;
				})
				.orElseGet(Set::of);
	}

	/**
	 * 중심점이 가장 가까운 활성 장소. {@link #NEAREST_PLACE_LIMIT_METERS} 밖이면 빈 값이다.
	 *
	 * <p>폴리곤이 아니라 중심점으로 재는 것은 "가까운 장소"를 고르는 데는 충분하고,
	 * 폴리곤 안이었다면 앞의 격자 판정에서 이미 잡혔을 것이기 때문이다.
	 */
	private Optional<Long> nearestPlaceId(BigDecimal latitude, BigDecimal longitude) {
		double fromLatitude = latitude.doubleValue();
		double fromLongitude = longitude.doubleValue();

		return placeRepository.findAllByActiveTrueOrderByNameAsc().stream()
				.filter(place -> place.getCenterLatitude() != null && place.getCenterLongitude() != null)
				.map(place -> new PlaceDistance(place, Distances.metersBetween(
						fromLatitude, fromLongitude,
						place.getCenterLatitude().doubleValue(), place.getCenterLongitude().doubleValue())))
				.filter(candidate -> candidate.meters() <= NEAREST_PLACE_LIMIT_METERS)
				.min(Comparator.comparingDouble(PlaceDistance::meters))
				.map(candidate -> candidate.place().getId());
	}

	private record PlaceDistance(Place place, double meters) {
	}
}
