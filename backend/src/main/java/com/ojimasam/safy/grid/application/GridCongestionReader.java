package com.ojimasam.safy.grid.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.grid.domain.PlaceGridMapping;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

/**
 * 격자 셀별 공공데이터 혼잡도. 격자 위험도 계산의 두 입력 중 하나다.
 *
 * <p>한 셀에 여러 장소가 걸치면 <b>가장 높은 혼잡 등급</b>을 쓴다(격자 명세 6.2).
 * 실측상 121곳 중 겹치는 장소가 있어 7,346개 매핑이 6,782개 셀에 몰린다.
 *
 * <p>제보의 장소 귀속({@code PlaceLocator})은 반대로 <b>가장 작은 장소</b>를 고른다.
 * 그쪽은 제보 위치를 설명하는 용도이고 이쪽은 위험도 입력이라 규칙이 다르다.
 */
@Component
public class GridCongestionReader {
	private final CityCongestionObservationRepository observationRepository;
	private final PlaceGridMappingRepository placeGridMappingRepository;

	public GridCongestionReader(
			CityCongestionObservationRepository observationRepository,
			PlaceGridMappingRepository placeGridMappingRepository
	) {
		this.observationRepository = observationRepository;
		this.placeGridMappingRepository = placeGridMappingRepository;
	}

	/**
	 * 관측값이 있는 셀만 담긴다. 값이 없는 셀은 키가 없다 — 혼잡도 0 이 아니라
	 * "근거 없음"이고, 격자 위험도는 그 구분을 그대로 쓴다.
	 *
	 * @param since 이 시각 이후에 관측된 값만 쓴다. {@code findLatestPerPlace} 는 시각과
	 *        무관하게 장소별 최신 1건을 주므로, 수집이 멈추면 오래된 관측이 계속 최신으로
	 *        잡힌다. 격자 명세 7.3 은 시간 창 밖의 관측을 쓰지 않는다고 정한다.
	 */
	@Transactional(readOnly = true)
	public Map<Long, CongestionLevel> latestByGridCell(Instant since) {
		Map<Long, CongestionLevel> levelByPlace = new HashMap<>();
		for (CityCongestionObservation observation : observationRepository.findLatestPerPlace()) {
			if (observation.getObservedAt().isBefore(since)) {
				continue;
			}
			levelByPlace.put(observation.getPlace().getId(), observation.getCongestionLevel());
		}
		if (levelByPlace.isEmpty()) {
			return Map.of();
		}

		Map<Long, CongestionLevel> levelByCell = new HashMap<>();
		for (PlaceGridMapping mapping : placeGridMappingRepository.findAll()) {
			CongestionLevel level = levelByPlace.get(mapping.getPlaceId());
			if (level == null) {
				continue;
			}
			levelByCell.merge(mapping.getGridCellId(), level, GridCongestionReader::higher);
		}

		return Map.copyOf(levelByCell);
	}

	/**
	 * enum 선언 순서가 곧 혼잡 강도다(RELAXED &lt; NORMAL &lt; SLIGHTLY_CROWDED &lt; CROWDED).
	 * 순서를 바꾸면 이 비교가 조용히 뒤집히므로 테스트로 고정해 둔다.
	 */
	private static CongestionLevel higher(CongestionLevel left, CongestionLevel right) {
		return left.compareTo(right) >= 0 ? left : right;
	}
}
