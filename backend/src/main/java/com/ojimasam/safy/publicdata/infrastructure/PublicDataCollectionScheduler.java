package com.ojimasam.safy.publicdata.infrastructure;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.publicdata.application.CityCongestionIngestService;
import com.ojimasam.safy.publicdata.application.CulturalEventIngestService;
import com.ojimasam.safy.publicdata.application.DisasterMessageIngestService;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageCollectionRequest;

/**
 * 주기적으로 FastAPI 에 수집을 요청하고 결과를 적재한다.
 *
 * 수집 대상 장소 목록의 단일 진실 원천은 places 테이블이다.
 * FastAPI 는 목록을 보관하지 않고 매 요청에서 받은 코드만 조회하므로,
 * 지역을 추가·제외할 때 이 테이블만 고치면 된다.
 *
 * fixedRate 라서 주기는 실행 시작 시각 기준이다(완료 시각 기준이 아니다).
 * 수집이 주기보다 오래 걸리면 다음 실행이 늦게 시작하지만, 같은 작업이 겹쳐 실행되지는 않는다.
 * read-timeout(2분)이 주기(혼잡도 3분)보다 짧아 한 회차가 다음 회차를 밀어내지 않는다.
 */
@Component
public class PublicDataCollectionScheduler {
	private static final Logger log = LoggerFactory.getLogger(PublicDataCollectionScheduler.class);

	private final PublicDataClient publicDataClient;
	private final PlaceRepository placeRepository;
	private final CityCongestionIngestService cityCongestionIngestService;
	private final CulturalEventIngestService culturalEventIngestService;
	private final DisasterMessageIngestService disasterMessageIngestService;
	private final PublicDataProperties publicDataProperties;

	public PublicDataCollectionScheduler(
			PublicDataClient publicDataClient,
			PlaceRepository placeRepository,
			CityCongestionIngestService cityCongestionIngestService,
			CulturalEventIngestService culturalEventIngestService,
			DisasterMessageIngestService disasterMessageIngestService,
			PublicDataProperties publicDataProperties
	) {
		this.publicDataClient = publicDataClient;
		this.placeRepository = placeRepository;
		this.cityCongestionIngestService = cityCongestionIngestService;
		this.culturalEventIngestService = culturalEventIngestService;
		this.disasterMessageIngestService = disasterMessageIngestService;
		this.publicDataProperties = publicDataProperties;
	}

	@Scheduled(
			fixedRateString = "${app.public-data.city-congestion-delay:PT3M}",
			scheduler = "publicDataTaskScheduler"
	)
	public void collectCityCongestion() {
		List<String> areaCodes = activeAreaCodes();
		if (areaCodes.isEmpty()) {
			return;
		}
		publicDataClient.fetchCityCongestion(areaCodes).ifPresent(response -> {
			int saved = cityCongestionIngestService.ingest(response);
			if (saved > 0) {
				log.info("인구 밀집 관측값 {}건을 적재했습니다.", saved);
			}
		});
	}

	@Scheduled(
			cron = "${app.public-data.cultural-event-cron:0 0 4 * * *}",
			scheduler = "publicDataTaskScheduler"
	)
	public void collectCulturalEvents() {
		List<String> areaCodes = activeAreaCodes();
		if (areaCodes.isEmpty()) {
			return;
		}
		publicDataClient.fetchCulturalEvents(areaCodes).ifPresent(response -> {
			int saved = culturalEventIngestService.ingest(response);
			log.info("문화 행사 {}건을 새로 적재했습니다.", saved);
		});
	}

	@Scheduled(
			fixedRateString = "${app.public-data.disaster-message-delay:PT2M}",
			scheduler = "publicDataTaskScheduler"
	)
	public void collectDisasterMessages() {
		DisasterMessageCollectionRequest request = DisasterMessageCollectionRequest.lookingBack(
				publicDataProperties.disasterMessageLookbackDays()
		);
		publicDataClient.fetchDisasterMessages(request).ifPresent(response -> {
			int saved = disasterMessageIngestService.ingest(response);
			if (saved > 0) {
				log.info("재난 문자 {}건을 적재했습니다.", saved);
			}
		});
	}

	/**
	 * places 가 비어 있으면 FastAPI 를 호출하지 않는다.
	 * 빈 목록을 보내면 FastAPI 가 아무것도 조회하지 못하는 요청을 매 주기 처리하게 된다.
	 */
	private List<String> activeAreaCodes() {
		List<String> areaCodes = placeRepository.findAllByActiveTrueOrderByNameAsc()
				.stream()
				.map(Place::getExternalPlaceId)
				.toList();
		if (areaCodes.isEmpty()) {
			log.warn("places 에 활성 지역이 없어 공공데이터 수집을 건너뜁니다. seed 를 먼저 등록해야 합니다.");
		}
		return areaCodes;
	}
}
