package com.ojimasam.safy.place.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceBoundary;
import com.ojimasam.safy.place.domain.PlaceRepository;

/**
 * 읽을 수 있는 장소 경계를 메모리에 올려 두고 나눠 준다.
 *
 * <p>폴리곤 총 정점이 12,226개라 매번 파싱하면 비싸다. 장소는 시드로 들어오고 거의
 * 바뀌지 않으므로 첫 호출에 한 번 읽고 그대로 쓴다. 장소를 추가·수정했다면
 * 애플리케이션을 다시 띄워야 반영된다.
 */
@Component
public class PlaceBoundaries {
	private static final Logger log = LoggerFactory.getLogger(PlaceBoundaries.class);

	private final PlaceRepository placeRepository;

	private volatile List<PlaceBoundary> cached;

	public PlaceBoundaries(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/**
	 * 경계를 읽을 수 있는 장소 전부. <b>면적 오름차순</b>이다.
	 *
	 * <p>정렬 순서는 겹친 장소 중 가장 작은 것을 먼저 만나야 하는 {@link PlaceLocator}
	 * 때문이다. 순서에 의존하지 않는 쪽에서는 그냥 목록으로 쓰면 된다.
	 */
	public List<PlaceBoundary> all() {
		List<PlaceBoundary> boundaries = cached;
		if (boundaries != null) {
			return boundaries;
		}

		synchronized (this) {
			if (cached == null) {
				cached = load();
			}
			return cached;
		}
	}

	private List<PlaceBoundary> load() {
		List<PlaceBoundary> loaded = new ArrayList<>();

		for (Place place : placeRepository.findAllByActiveTrueOrderByNameAsc()) {
			PlaceBoundary.from(place).ifPresent(loaded::add);
		}

		loaded.sort(Comparator.comparingDouble(PlaceBoundary::area));
		log.info("장소 경계 {}건을 메모리에 올렸습니다.", loaded.size());
		return List.copyOf(loaded);
	}
}
