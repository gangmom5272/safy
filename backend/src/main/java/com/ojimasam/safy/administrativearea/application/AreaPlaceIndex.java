package com.ojimasam.safy.administrativearea.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;

/**
 * 서울시 121장소가 각각 어느 자치구에 속하는지 알려 준다.
 *
 * <p>{@code administrative_areas.boundary_geojson} 은 시드에 없어 비어 있다. 자치구 폴리곤이
 * 없으니 {@code PlaceLocator} 처럼 좌표를 직접 판정할 수 없고, 제보 라우팅이 쓰는 것과 같은
 * 좌표→자치구 해석기로 장소 중심점을 해석한다. 해석기는 Redis 로 30일 캐싱하므로 재기동
 * 후에도 카카오를 다시 부르지 않는다.
 *
 * <p>중심점 기준이라 장소는 자치구 하나에만 속한다. 한강공원은 서울시 목록에서 이미
 * 구간별로 나뉘어 있어(반포·잠실·여의도·뚝섬…) 여러 구에 걸치는 장소가 사실상 없다.
 *
 * <p>121회 해석은 요청을 막을 만큼 느리므로 <b>절대 요청 스레드에서 만들지 않는다.</b>
 * 준비되기 전 조회는 빈 결과를 주고(화면은 '정보 없음'), 뒤에서 만들어진 다음 조회부터
 * 값이 나온다. 기동 직후 한 번 미리 만들어 두므로 사용자가 빈 결과를 볼 일은 드물다.
 */
@Component
public class AreaPlaceIndex {
	private static final Logger log = LoggerFactory.getLogger(AreaPlaceIndex.class);

	private final PlaceRepository placeRepository;
	private final AdministrativeAreaResolver resolver;

	private volatile Map<String, Set<Long>> placeIdsByAreaCode;
	private final AtomicBoolean building = new AtomicBoolean();

	public AreaPlaceIndex(PlaceRepository placeRepository, AdministrativeAreaResolver resolver) {
		this.placeRepository = placeRepository;
		this.resolver = resolver;
	}

	/**
	 * 자치구 코드에 속한 장소 ID. 색인이 아직 없으면 빈 집합을 주고 만들기를 시작한다.
	 *
	 * <p>빈 집합은 "이 구에 장소가 없다"와 "아직 모른다"를 구분하지 않는다. 둘 다 화면에서는
	 * 혼잡도 정보 없음이고, 121장소가 없는 자치구는 실제로 존재하므로 구분해도 쓸 데가 없다.
	 */
	public Set<Long> placeIdsOf(String areaCode) {
		Map<String, Set<Long>> index = placeIdsByAreaCode;
		if (index == null) {
			refreshInBackground();
			return Set.of();
		}
		return index.getOrDefault(areaCode, Set.of());
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmUp() {
		refreshInBackground();
	}

	private void refreshInBackground() {
		if (placeIdsByAreaCode != null || !building.compareAndSet(false, true)) {
			return;
		}
		CompletableFuture.runAsync(this::refresh);
	}

	private void refresh() {
		try {
			Map<String, Set<Long>> index = build();
			// 전부 실패했으면 캐시하지 않는다. 카카오 키 누락·장애로 만든 빈 색인을 굳혀 두면
			// 설정을 고쳐도 재기동 전까지 혼잡도가 계속 비어 있다.
			if (index.isEmpty()) {
				log.warn("장소를 자치구로 해석하지 못해 색인을 만들지 않았습니다. 다음 조회에서 다시 시도합니다.");
				return;
			}
			placeIdsByAreaCode = index;
			log.info("자치구 {}곳에 장소를 연결했습니다.", index.size());
		} catch (RuntimeException exception) {
			log.warn("장소-자치구 색인 생성에 실패했습니다.", exception);
		} finally {
			building.set(false);
		}
	}

	private Map<String, Set<Long>> build() {
		List<Place> places = placeRepository.findAllByActiveTrueOrderByNameAsc();
		Map<String, Set<Long>> index = new HashMap<>();

		for (Place place : places) {
			if (place.getCenterLatitude() == null || place.getCenterLongitude() == null) {
				continue;
			}
			try {
				resolver.resolve(place.getCenterLatitude(), place.getCenterLongitude())
						.ifPresent(area -> index
								.computeIfAbsent(area.code(), code -> new HashSet<>())
								.add(place.getId()));
			} catch (AdministrativeAreaResolutionException exception) {
				// 장소 한 곳을 해석하지 못해도 나머지 120곳의 혼잡도는 보여줄 수 있다.
				log.warn("장소 '{}'의 자치구를 해석하지 못했습니다: {}", place.getName(), exception.getMessage());
			}
		}

		index.replaceAll((code, placeIds) -> Set.copyOf(placeIds));
		return Map.copyOf(index);
	}
}
