package com.ojimasam.safy.place.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 장소 하나의 경계 폴리곤. 첫 링이 외곽이고 나머지는 구멍이다(GeoJSON 규약).
 * 121장소 중 북서울꿈의숲 한 곳에 구멍이 있다.
 *
 * <p>PostGIS 없이 ray casting 으로 판정한다. 폴리곤 총 정점이 12,226개라 파싱 결과를
 * 메모리에 한 번 올려 두면 판정은 정점 수에 비례하는 산술 연산으로 끝난다.
 *
 * <p>좌표가 어느 장소에 속하는지(제보 → 장소)와 장소가 어느 셀을 덮는지(장소 → 격자)
 * 양쪽에서 쓴다. 두 방향이 같은 판정을 써야 "장소 안"의 정의가 하나로 유지된다.
 */
public record PlaceBoundary(
		Long placeId,
		List<Ring> rings,
		double minLatitude,
		double maxLatitude,
		double minLongitude,
		double maxLongitude,
		double area
) {
	private static final Logger log = LoggerFactory.getLogger(PlaceBoundary.class);

	/**
	 * 이 프로젝트에는 {@code ObjectMapper} 빈이 없어 자체 인스턴스를 쓴다.
	 * 경계 GeoJSON 을 읽기만 하므로 기본 설정으로 충분하고, ObjectMapper 는 thread-safe 다.
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 경계가 없거나 Polygon 이 아니거나 읽지 못하면 빈 값이다. */
	public static Optional<PlaceBoundary> from(Place place) {
		if (place.getBoundaryGeoJson() == null || place.getBoundaryGeoJson().isBlank()) {
			return Optional.empty();
		}

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

			return Optional.of(of(place.getId(), rings));
		} catch (Exception exception) {
			// 한 장소의 경계가 깨져도 나머지 장소 처리는 계속되어야 한다.
			log.warn("장소 {} 의 경계를 읽지 못해 건너뜁니다.", place.getExternalPlaceId(), exception);
			return Optional.empty();
		}
	}

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

	public boolean contains(double longitude, double latitude) {
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

	public record Ring(double[] longitudes, double[] latitudes) {
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
}
