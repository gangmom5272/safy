package com.ojimasam.safy.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.place.infrastructure.JpaPlaceRepository;
import com.ojimasam.safy.publicdata.application.dto.CityCongestionResponse;
import com.ojimasam.safy.publicdata.application.dto.CollectionRunPayload;
import com.ojimasam.safy.publicdata.application.dto.CulturalEventResponse;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;
import com.ojimasam.safy.publicdata.infrastructure.JpaCityCongestionObservationRepository;
import com.ojimasam.safy.publicdata.infrastructure.JpaCollectionRunRepository;
import com.ojimasam.safy.publicdata.infrastructure.JpaCulturalEventRepository;
import com.ojimasam.safy.publicdata.infrastructure.JpaDisasterMessageRepository;

@DataJpaTest
@ActiveProfiles("test")
@Import({
		JpaAuditingConfig.class,
		CollectionRunRecorder.class,
		CityCongestionIngestService.class,
		CulturalEventIngestService.class,
		DisasterMessageIngestService.class,
		DisasterMessageWriter.class
})
class PublicDataIngestServiceTest {
	private static final String AREA_CODE = "POI001";
	private static final Instant OBSERVED_AT = Instant.parse("2026-07-29T04:00:00Z");

	@Autowired
	private CityCongestionIngestService cityCongestionIngestService;

	@Autowired
	private CulturalEventIngestService culturalEventIngestService;

	@Autowired
	private DisasterMessageIngestService disasterMessageIngestService;

	@Autowired
	private JpaCityCongestionObservationRepository observationRepository;

	@Autowired
	private JpaCulturalEventRepository culturalEventRepository;

	@Autowired
	private JpaDisasterMessageRepository disasterMessageRepository;

	@Autowired
	private JpaCollectionRunRepository collectionRunRepository;

	@Autowired
	private JpaPlaceRepository placeRepository;

	@BeforeEach
	void seedPlace() {
		placeRepository.saveAndFlush(
				Place.create(
						AREA_CODE,
						"강남 MICE 관광특구",
						PlaceCategory.TOURIST_ZONE,
						new BigDecimal("37.5087550"),
						new BigDecimal("127.0631350"),
						"seoul-citydata"
				)
		);
	}

	@Test
	void ingestsCongestionOnceForRepeatedResponse() {
		CityCongestionResponse response = congestionResponse("run-congestion-1", AREA_CODE, "약간 붐빔");

		assertThat(cityCongestionIngestService.ingest(response)).isEqualTo(1);
		assertThat(cityCongestionIngestService.ingest(response)).isZero();

		assertThat(observationRepository.count()).isEqualTo(1);
		assertThat(collectionRunRepository.count()).isEqualTo(1);
	}

	/** FastAPI 가 영문 enum 으로 보내도 한글 원문으로 보내도 같은 값으로 적재되어야 한다. */
	@Test
	void acceptsBothCongestionLevelNotations() {
		assertThat(cityCongestionIngestService.ingest(
				congestionResponse("run-congestion-2", AREA_CODE, "SLIGHTLY_CROWDED")
		)).isEqualTo(1);

		assertThat(observationRepository.findAll().getFirst().getCongestionLevel().getLabel())
				.isEqualTo("약간 붐빔");
	}

	@Test
	void skipsUnmappedAreaAndUnknownLevel() {
		assertThat(cityCongestionIngestService.ingest(
				congestionResponse("run-congestion-3", "POI-없음", "약간 붐빔")
		)).isZero();
		assertThat(cityCongestionIngestService.ingest(
				congestionResponse("run-congestion-4", AREA_CODE, "매우 한가함")
		)).isZero();

		assertThat(observationRepository.count()).isZero();
	}

	/** 수집 실행 식별자가 없으면 중복 판정을 할 수 없으므로 배치 전체를 건너뛴다. */
	@Test
	void skipsBatchWithoutExternalRunId() {
		CityCongestionResponse response = new CityCongestionResponse(
				new CollectionRunPayload(
						null,
						"seoul-citydata",
						OBSERVED_AT,
						OBSERVED_AT,
						"SUCCESS",
						1,
						0,
						null,
						null,
						null
				),
				List.of(congestionItem(AREA_CODE, "약간 붐빔"))
		);

		assertThat(cityCongestionIngestService.ingest(response)).isZero();
		assertThat(observationRepository.count()).isZero();
		assertThat(collectionRunRepository.count()).isZero();
	}

	@Test
	void updatesEventInsteadOfInsertingDuplicate() {
		assertThat(culturalEventIngestService.ingest(eventResponse("run-event-1", "여의도 봄꽃축제"))).isEqualTo(1);
		assertThat(culturalEventIngestService.ingest(eventResponse("run-event-2", "여의도 봄꽃축제(연장)"))).isZero();

		assertThat(culturalEventRepository.count()).isEqualTo(1);
		assertThat(culturalEventRepository.findAll().getFirst().getTitle()).isEqualTo("여의도 봄꽃축제(연장)");
	}

	@Test
	void skipsEventWithoutExternalId() {
		CulturalEventResponse response = new CulturalEventResponse(
				runPayload("run-event-3"),
				List.of(new CulturalEventResponse.Item(
						null,
						AREA_CODE,
						"강남 MICE 관광특구",
						"식별자 없는 행사",
						"2026-07-01~2026-08-30",
						LocalDate.of(2026, 7, 1),
						LocalDate.of(2026, 8, 30),
						"코엑스",
						null,
						null,
						false,
						null,
						null,
						null
				))
		);

		assertThat(culturalEventIngestService.ingest(response)).isZero();
		assertThat(culturalEventRepository.count()).isZero();
	}

	@Test
	void ingestsDisasterMessageOnceForRepeatedResponse() {
		DisasterMessageResponse response = new DisasterMessageResponse(
				runPayload("run-disaster-1"),
				List.of(new DisasterMessageResponse.Item(
						"SN-20260729-0001",
						OBSERVED_AT,
						"강남구 일대 인구 밀집으로 통행에 주의하십시오.",
						List.of("서울특별시 강남구", "서울특별시 서초구"),
						"안전안내",
						"인구밀집"
				))
		);

		assertThat(disasterMessageIngestService.ingest(response)).isEqualTo(1);
		assertThat(disasterMessageIngestService.ingest(response)).isZero();

		assertThat(disasterMessageRepository.count()).isEqualTo(1);
		assertThat(disasterMessageRepository.findAll().getFirst().getRegions())
				.containsExactly("서울특별시 강남구", "서울특별시 서초구");
	}

	private CityCongestionResponse congestionResponse(
			String externalRunId,
			String areaCode,
			String congestionLevel
	) {
		return new CityCongestionResponse(
				runPayload(externalRunId),
				List.of(congestionItem(areaCode, congestionLevel))
		);
	}

	private CityCongestionResponse.Item congestionItem(String areaCode, String congestionLevel) {
		return new CityCongestionResponse.Item(
				areaCode,
				"강남 MICE 관광특구",
				congestionLevel,
				"사람들이 몰려있을 가능성이 있어요.",
				12000,
				14000,
				OBSERVED_AT
		);
	}

	private CulturalEventResponse eventResponse(String externalRunId, String title) {
		return new CulturalEventResponse(
				runPayload(externalRunId),
				List.of(new CulturalEventResponse.Item(
						"seoul-citydata:POI001:강남 MICE 야간개장:2026-07-01~2026-08-30",
						AREA_CODE,
						"강남 MICE 관광특구",
						title,
						"2026-07-01~2026-08-30",
						LocalDate.of(2026, 7, 1),
						LocalDate.of(2026, 8, 30),
						"코엑스",
						new BigDecimal("37.5087550"),
						new BigDecimal("127.0631350"),
						false,
						"https://example.test/thumb.jpg",
						"https://example.test/detail",
						"행사 설명"
				))
		);
	}

	private CollectionRunPayload runPayload(String externalRunId) {
		return new CollectionRunPayload(
				externalRunId,
				"seoul-citydata",
				OBSERVED_AT,
				OBSERVED_AT,
				"SUCCESS",
				1,
				0,
				null,
				null,
				null
		);
	}
}
