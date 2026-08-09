package com.ojimasam.safy.place.application;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ojimasam.safy.place.domain.PlaceBoundary;

/**
 * 좌표가 서울 121장소 중 어디에 속하는지 판정한다.
 *
 * <p>중심점과 반경으로는 판정할 수 없다. 장소마다 크기와 모양이 크게 달라
 * (강남역 9정점, 남산공원 1084정점) 하나의 반경으로 대체할 수 없고, 한강공원처럼
 * 길쭉한 장소는 어떤 반경을 써도 맞지 않는다. 그래서 실제 폴리곤으로 판정한다.
 */
@Component
public class PlaceLocator {
	private final PlaceBoundaries placeBoundaries;

	public PlaceLocator(PlaceBoundaries placeBoundaries) {
		this.placeBoundaries = placeBoundaries;
	}

	/**
	 * 좌표를 포함하는 장소의 ID. 없으면 빈 값이다.
	 *
	 * <p>장소는 서로 겹친다 — 홍대입구역은 홍대 관광특구 안에 있고, 이태원역은 이태원
	 * 관광특구 안에 있다(중심점 기준 19곳). 여러 곳에 속하면 <b>가장 작은 장소</b>를
	 * 고른다. 제보 위치를 설명할 때 "홍대 관광특구"보다 "홍대입구역"이 유용하다.
	 *
	 * <p>격자 위험도의 겹침 규칙은 이와 다르다. 그쪽은 가장 높은 혼잡 등급을 쓴다
	 * (격자 명세 6.2). 하나는 제보 위치 표시용이고 하나는 위험도 계산 입력이다.
	 */
	public Optional<Long> locate(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null) {
			return Optional.empty();
		}

		double pointLatitude = latitude.doubleValue();
		double pointLongitude = longitude.doubleValue();

		// 면적 오름차순이라 처음 걸리는 것이 가장 작은 장소다.
		for (PlaceBoundary boundary : placeBoundaries.all()) {
			if (boundary.contains(pointLongitude, pointLatitude)) {
				return Optional.of(boundary.placeId());
			}
		}

		return Optional.empty();
	}
}
