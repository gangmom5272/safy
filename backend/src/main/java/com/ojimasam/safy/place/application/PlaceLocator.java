package com.ojimasam.safy.place.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;

/**
 * 좌표가 서울 121장소 중 어디에 속하는지 판정한다.
 *
 * <p>중심점과 반경으로는 판정할 수 없다. 장소마다 크기와 모양이 크게 달라
 * (강남역 9정점, 남산공원 1084정점) 하나의 반경으로 대체할 수 없고, 한강공원처럼
 * 길쭉한 장소는 어떤 반경을 써도 맞지 않는다. 그래서 실제 폴리곤으로 판정한다.
 *
 * <p>PostGIS 없이 ray casting 으로 계산한다. 폴리곤 총 정점이 12,226개라 파싱 결과를
 * 메모리에 한 번만 올려 두면 판정은 정점 수에 비례하는 산술 연산으로 끝난다.
 * 장소 목록은 시드로 들어오고 거의 바뀌지 않으므로 첫 호출에 캐시하고 그대로 쓴다.
 * 장소를 추가·수정했다면 애플리케이션을 다시 띄워야 반영된다.
 */
@Component
public class PlaceLocator {
	private static final Logger log = LoggerFactory.getLogger(PlaceLocator.class);

	/**
	 * 이 프로젝트에는 {@code ObjectMapper} 빈이 없어 자체 인스턴스를 쓴다.
	 * 경계 GeoJSON 을 읽기만 하므로 기본 설정으로 충분하고, ObjectMapper 는 thread-safe 다.
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final PlaceRepository placeRepository;

	private volatile List<PlaceBoundary> boundaries;

	public PlaceLocator(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * 좌표를 포함하는 장소의 ID. 없으면 빈 값이다.
	 *
	 * <p>장소는 서로 겹친다 — 홍대입구역은 홍대 관광특구 안에 있고, 이태원역은 이태원
	 * 관광특구 안에 있다(중심점 기준 19곳). 여러 곳에 속하면 <b>가장 작은 장소</b>를
	 * 고른다. 제보 위치를 설명할 때 "홍대 관광특구"보다 "홍대입구역"이 유용하다.
	 */
	public Optional<Long> locate(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			return Optional.empty();
		}

		double pointLatitude = latitude.doubleValue();
		double pointLongitude = longitude.doubleValue();

		// 면적 오름차순이라 처음 걸리는 것이 가장 작은 장소다.
		for (PlaceBoundary boundary : cachedBoundaries()) {
			if (boundary.contains(pointLongitude, pointLatitude)) {
				return Optional.of(boundary.placeId());
			}
		}

		return Optional.empty();
	}

	private List<PlaceBoundary> cachedBoundaries() {
		List<PlaceBoundary> cached = boundaries;
		if (cached != null) {
			return cached;
		}

		synchronized (this) {
			if (boundaries == null) {
				boundaries = loadBoundaries();
			}
			return boundaries;
		}
	}

	private List<PlaceBoundary> loadBoundaries() {
		List<PlaceBoundary> loaded = new ArrayList<>();

		for (Place place : placeRepository.findAllByActiveTrueOrderByNameAsc()) {
			if (place.getBoundaryGeoJson() == null || place.getBoundaryGeoJson().isBlank()) {
				continue;
			}
			parse(place).ifPresent(loaded::add);
		}

		// 겹치는 장소 중 가장 작은 것을 먼저 만나도록 정렬한다.
		loaded.sort(Comparator.comparingDouble(PlaceBoundary::area));
		log.info("장소 경계 {}건을 메모리에 올렸습니다.", loaded.size());
		return List.copyOf(loaded);
	}

	private Optional<PlaceBoundary> parse(Place place) {
		try {
			JsonNode geometry = OBJECT_MAPPER.readTree(place.getBoundaryGeoJson());
			if (!"Polygon".equals(geometry.path("type").asText())) {
				log.warn("장소 {} 의 경계가 Polygon 이 아니라 건너뜁니다.", place.getExternalPlaceId());
				return Optional.empty();
			}

			List<Ring> rings = new ArrayList<>();
			for (JsonNode ringNode : geometry.path("coordinates")) {
				rings.add(toRing(ringNode));
			}
			if (rings.isEmpty()) {
				return Optional.empty();
			}

			return Optional.of(PlaceBoundary.of(place.getId(), rings));
		} catch (Exception exception) {
			// 한 장소의 경계가 깨져도 나머지 판정은 계속되어야 한다.
			log.warn("장소 {} 의 경계를 읽지 못해 건너뜁니다.", place.getExternalPlaceId(), exception);
			return Optional.empty();
		}
	}

	/** GeoJSON 좌표는 [경도, 위도] 순서다. */
	private static Ring toRing(JsonNode ringNode) {
		int size = ringNode.size();
		double[] longitudes = new double[size];
		double[] latitudes = new double[size];

		for (int index = 0; index < size; index++) {
			longitudes[index] = ringNode.get(index).get(0).asDouble();
			latitudes[index] = ringNode.get(index).get(1).asDouble();
		}

		return new Ring(longitudes, latitudes);
	}

	private record Ring(double[] longitudes, double[] latitudes) {
		/** ray casting. 경계선에 정확히 놓인 점의 판정은 정의하지 않는다. */
		boolean contains(double longitude, double latitude) {
			boolean inside = false;

			for (int current = 0, previous = longitudes.length - 1;
					current < longitudes.length;
					previous = current++) {
				double currentLongitude = longitudes[current];
				double currentLatitude = latitudes[current];
				double previousLongitude = longitudes[previous];
				double previousLatitude = latitudes[previous];

				boolean crossesLatitude = (currentLatitude > latitude) != (previousLatitude > latitude);
				if (!crossesLatitude) {
					continue;
				}

				double crossingLongitude = (previousLongitude - currentLongitude)
						* (latitude - currentLatitude)
						/ (previousLatitude - currentLatitude)
						+ currentLongitude;
				if (longitude < crossingLongitude) {
					inside = !inside;
				}
			}

			return inside;
		}

		double signedArea() {
			double sum = 0;

			for (int current = 0, previous = longitudes.length - 1;
					current < longitudes.length;
					previous = current++) {
				sum += longitudes[previous] * latitudes[current] - longitudes[current] * latitudes[previous];
			}

			return sum / 2;
		}
	}

	/**
	 * 장소 하나의 경계. 첫 링이 외곽이고 나머지는 구멍이다(GeoJSON 규약).
	 * 121장소 중 북서울꿈의숲 한 곳에 구멍이 있다.
	 */
	private record PlaceBoundary(
			Long placeId,
			List<Ring> rings,
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude,
			double area
	) {
		static PlaceBoundary of(Long placeId, List<Ring> rings) {
			Ring outer = rings.get(0);
			double minLatitude = Double.MAX_VALUE;
			double maxLatitude = -Double.MAX_VALUE;
			double minLongitude = Double.MAX_VALUE;
			double maxLongitude = -Double.MAX_VALUE;

			for (int index = 0; index < outer.latitudes().length; index++) {
				minLatitude = Math.min(minLatitude, outer.latitudes()[index]);
				maxLatitude = Math.max(maxLatitude, outer.latitudes()[index]);
				minLongitude = Math.min(minLongitude, outer.longitudes()[index]);
				maxLongitude = Math.max(maxLongitude, outer.longitudes()[index]);
			}

			// 실제 면적이 아니라 도(degree) 기준 값이다. 겹친 장소끼리 크기를 비교하는
			// 데만 쓰므로 좌표계 변환은 필요하지 않다.
			double area = Math.abs(outer.signedArea());
			for (int index = 1; index < rings.size(); index++) {
				area -= Math.abs(rings.get(index).signedArea());
			}

			return new PlaceBoundary(placeId, rings, minLatitude, maxLatitude, minLongitude, maxLongitude, area);
		}

		boolean contains(double longitude, double latitude) {
			// 정점이 1084개인 장소도 있어 bbox 로 먼저 걸러 낸다.
			if (latitude < minLatitude || latitude > maxLatitude
					|| longitude < minLongitude || longitude > maxLongitude) {
				return false;
			}
			if (!rings.get(0).contains(longitude, latitude)) {
				return false;
			}

			for (int index = 1; index < rings.size(); index++) {
				if (rings.get(index).contains(longitude, latitude)) {
					return false;
				}
			}

			return true;
		}
	}
}
