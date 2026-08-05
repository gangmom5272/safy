package com.ojimasam.safy.common.geo;

/**
 * 위경도 두 점 사이 거리. PostGIS 를 쓰지 않으므로 여기서 계산한다.
 *
 * <p>좌표 반경 조회(제보 카운트)와 최근접 장소 찾기 양쪽에서 쓴다. 두 곳이 같은 식을
 * 써야 "1km 안"의 정의가 하나로 유지된다.
 */
public final class Distances {
	/** 지구 평균 반지름(m). 서울 범위에서 구면 근사 오차는 0.5% 미만이다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private Distances() {
	}

	/**
	 * haversine 거리(m).
	 *
	 * <p>단순 평면 근사가 아니라 haversine 을 쓰는 이유는 경도 1도의 실제 거리가 위도에
	 * 따라 달라지기 때문이다. 서울 안이라 차이가 크지는 않지만, 반경 경계에 걸친 제보의
	 * 포함 여부가 뒤집히지 않게 정직한 식을 쓴다.
	 */
	public static double metersBetween(
			double fromLatitude, double fromLongitude,
			double toLatitude, double toLongitude
	) {
		double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
		double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
		double fromLatitudeRadians = Math.toRadians(fromLatitude);
		double toLatitudeRadians = Math.toRadians(toLatitude);

		double halfChordSquared = Math.pow(Math.sin(latitudeDelta / 2), 2)
				+ Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
				* Math.pow(Math.sin(longitudeDelta / 2), 2);

		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(halfChordSquared)));
	}

	/**
	 * 반경을 감싸는 위도 폭(도). bbox 로 1차 필터할 때 쓴다.
	 *
	 * <p>bbox 는 원보다 넓으므로 통과한 뒤 반드시 실제 거리로 다시 걸러야 한다.
	 */
	public static double latitudeSpanFor(double radiusMeters) {
		return Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
	}

	/**
	 * 반경을 감싸는 경도 폭(도). 위도가 높아질수록 같은 각도가 짧은 거리를 뜻해 폭이 넓어진다.
	 *
	 * <p>극지방에서 0 으로 나누는 것을 막으려 최소값을 둔다 — 서울에서는 걸릴 일이 없지만
	 * 잘못된 좌표가 들어와도 조용히 깨지지 않게 한다.
	 */
	public static double longitudeSpanFor(double radiusMeters, double atLatitude) {
		double cosine = Math.max(Math.cos(Math.toRadians(atLatitude)), 1e-6);
		return Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosine));
	}
}
