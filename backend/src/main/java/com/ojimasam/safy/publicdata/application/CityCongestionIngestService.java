package com.ojimasam.safy.publicdata.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.publicdata.application.dto.CityCongestionResponse;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;

/**
 * 지역별 인구 밀집 관측값을 적재한다.
 * 같은 (지역, 관측 시각)은 재폴링으로 다시 들어오므로 저장 전에 걸러낸다.
 */
@Service
public class CityCongestionIngestService {
	private static final Logger log = LoggerFactory.getLogger(CityCongestionIngestService.class);

	private final CollectionRunRecorder collectionRunRecorder;
	private final CityCongestionObservationRepository observationRepository;
	private final PlaceRepository placeRepository;
	private final Clock clock;

	@Autowired
	public CityCongestionIngestService(
			CollectionRunRecorder collectionRunRecorder,
			CityCongestionObservationRepository observationRepository,
			PlaceRepository placeRepository
	) {
		this(collectionRunRecorder, observationRepository, placeRepository, Clock.systemUTC());
	}

	CityCongestionIngestService(
			CollectionRunRecorder collectionRunRecorder,
			CityCongestionObservationRepository observationRepository,
			PlaceRepository placeRepository,
			Clock clock
	) {
		this.collectionRunRecorder = collectionRunRecorder;
		this.observationRepository = observationRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional
	public int ingest(CityCongestionResponse response) {
		if (response == null) {
			return 0;
		}
		Optional<CollectionRun> run = collectionRunRecorder.record(
				response.collectionRun(),
				CollectionJobType.CITY_CONGESTION
		);
		if (run.isEmpty()) {
			return 0;
		}

		Instant collectedAt = clock.instant();
		SkipTally tally = new SkipTally();
		int saved = 0;
		for (CityCongestionResponse.Item item : response.safeItems()) {
			if (save(item, run.get(), collectedAt, tally)) {
				saved++;
			}
		}
		tally.log();
		return saved;
	}

	private boolean save(
			CityCongestionResponse.Item item,
			CollectionRun run,
			Instant collectedAt,
			SkipTally tally
	) {
		if (item.areaCode() == null || item.observedAt() == null) {
			tally.malformed(item.areaCode());
			return false;
		}

		Optional<Place> place = placeRepository.findByExternalPlaceId(item.areaCode());
		if (place.isEmpty()) {
			// 이 주기의 값은 버려진다. place 를 seed 에 추가하면 다음 주기부터 정상 적재된다.
			tally.unmappedPlace(item.areaCode());
			return false;
		}

		Optional<CongestionLevel> level = CongestionLevel.from(item.congestionLevel());
		if (level.isEmpty()) {
			tally.unknownLevel(item.congestionLevel());
			return false;
		}

		if (observationRepository.existsByPlace_IdAndObservedAt(place.get().getId(), item.observedAt())) {
			return false;
		}

		observationRepository.save(
				CityCongestionObservation.create(
						place.get(),
						run,
						item.areaCode(),
						item.areaName(),
						level.get(),
						item.congestionMessage(),
						item.populationMin(),
						item.populationMax(),
						item.observedAt(),
						collectedAt,
						run.getSource()
				)
		);
		return true;
	}

	/**
	 * 건너뛴 항목을 배치당 한 줄로 요약한다.
	 * 지역 121곳이 5분마다 들어오므로 항목마다 경고를 남기면 하루 3만 줄이 넘어가 로그를 볼 수 없게 된다.
	 * (places seed 가 비어 있는 동안에는 매 배치가 전부 미매핑으로 걸러진다.)
	 */
	private static final class SkipTally {
		private int malformed;
		private int unmappedPlace;
		private int unknownLevel;
		private String sampleAreaCode;
		private String sampleLevel;

		void malformed(String areaCode) {
			malformed++;
			rememberArea(areaCode);
		}

		void unmappedPlace(String areaCode) {
			unmappedPlace++;
			rememberArea(areaCode);
		}

		void unknownLevel(String congestionLevel) {
			unknownLevel++;
			if (sampleLevel == null) {
				sampleLevel = congestionLevel;
			}
		}

		private void rememberArea(String areaCode) {
			if (sampleAreaCode == null) {
				sampleAreaCode = areaCode;
			}
		}

		void log() {
			if (malformed == 0 && unmappedPlace == 0 && unknownLevel == 0) {
				return;
			}
			log.warn(
					"혼잡도 항목을 건너뛰었습니다. 필수값누락={}, places미매핑={}, 알수없는혼잡도={}"
							+ " (예시 areaCode={}, 예시 혼잡도={})",
					malformed, unmappedPlace, unknownLevel, sampleAreaCode, sampleLevel
			);
		}
	}
}
