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
import com.ojimasam.safy.publicdata.application.dto.CulturalEventResponse;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.CulturalEvent;
import com.ojimasam.safy.publicdata.domain.CulturalEventRepository;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;

/**
 * 서울시 문화 행사를 적재한다.
 * 원본에 안정적인 식별자가 없어 FastAPI 가 합성한 externalId 를 upsert 키로 쓴다.
 */
@Service
public class CulturalEventIngestService {
	private static final Logger log = LoggerFactory.getLogger(CulturalEventIngestService.class);

	private final CollectionRunRecorder collectionRunRecorder;
	private final CulturalEventRepository culturalEventRepository;
	private final PlaceRepository placeRepository;
	private final Clock clock;

	@Autowired
	public CulturalEventIngestService(
			CollectionRunRecorder collectionRunRecorder,
			CulturalEventRepository culturalEventRepository,
			PlaceRepository placeRepository
	) {
		this(collectionRunRecorder, culturalEventRepository, placeRepository, Clock.systemUTC());
	}

	CulturalEventIngestService(
			CollectionRunRecorder collectionRunRecorder,
			CulturalEventRepository culturalEventRepository,
			PlaceRepository placeRepository,
			Clock clock
	) {
		this.collectionRunRecorder = collectionRunRecorder;
		this.culturalEventRepository = culturalEventRepository;
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional
	public int ingest(CulturalEventResponse response) {
		if (response == null) {
			return 0;
		}
		Optional<CollectionRun> run = collectionRunRecorder.record(
				response.collectionRun(),
				CollectionJobType.CULTURAL_EVENT
		);
		if (run.isEmpty()) {
			return 0;
		}

		Instant collectedAt = clock.instant();
		int saved = 0;
		for (CulturalEventResponse.Item item : response.safeItems()) {
			if (save(item, run.get(), collectedAt)) {
				saved++;
			}
		}
		return saved;
	}

	private boolean save(CulturalEventResponse.Item item, CollectionRun run, Instant collectedAt) {
		// externalId 가 없으면 같은 행사가 매 수집마다 새 행으로 쌓이므로 적재하지 않는다.
		if (item.externalId() == null || item.externalId().isBlank()) {
			log.warn("행사 식별자가 없어 건너뜁니다. name={}, areaCode={}", item.name(), item.areaCode());
			return false;
		}
		if (item.name() == null || item.name().isBlank() || item.areaCode() == null) {
			log.warn("행사명이나 지역 코드가 없어 건너뜁니다. externalId={}", item.externalId());
			return false;
		}

		Optional<Place> place = placeRepository.findByExternalPlaceId(item.areaCode());
		if (place.isEmpty()) {
			log.warn("places 에 없는 지역의 행사라 건너뜁니다. areaCode={}, name={}",
					item.areaCode(), item.name());
			return false;
		}

		CulturalEvent latest = CulturalEvent.create(
				place.get(),
				run,
				item.externalId(),
				item.name(),
				item.details(),
				item.place(),
				item.period(),
				item.startDate(),
				item.endDate(),
				item.latitude(),
				item.longitude(),
				item.areaCode(),
				item.areaName(),
				item.isPaid(),
				item.thumbnailUrl(),
				item.detailUrl(),
				run.getSource(),
				collectedAt
		);

		Optional<CulturalEvent> existing =
				culturalEventRepository.findBySourceAndExternalId(run.getSource(), item.externalId());
		if (existing.isPresent()) {
			existing.get().refreshFrom(latest);
			return false;
		}

		culturalEventRepository.save(latest);
		return true;
	}
}
