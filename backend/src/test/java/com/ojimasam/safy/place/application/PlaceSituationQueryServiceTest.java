package com.ojimasam.safy.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.administrativearea.application.AreaCongestionReader;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;
import com.ojimasam.safy.report.application.PlaceRecentReportCounter;

@ExtendWith(MockitoExtension.class)
class PlaceSituationQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
	private static final long PLACE_ID = 14L;

	@Mock PlaceRepository placeRepository;
	@Mock AreaCongestionReader areaCongestionReader;
	@Mock PlaceRecentReportCounter placeRecentReportCounter;

	@Test
	void combinesThePlacesOwnCongestionAndPolygonReportCount() {
		when(placeRepository.findOneById(PLACE_ID)).thenReturn(Optional.of(activePlace()));
		CongestionObservationView congestion = new CongestionObservationView(
				PLACE_ID, "역삼역", CongestionLevel.NORMAL, "여유롭습니다.", 10000, 12000, NOW);
		when(areaCongestionReader.atPlace(PLACE_ID)).thenReturn(Optional.of(congestion));
		when(placeRecentReportCounter.countWithin(PLACE_ID)).thenReturn(3L);

		var result = service().status(PLACE_ID);

		assertThat(result.placeId()).isEqualTo(PLACE_ID);
		assertThat(result.placeName()).isEqualTo("역삼역");
		assertThat(result.congestion()).isSameAs(congestion);
		assertThat(result.recentReportCount()).isEqualTo(3L);
		assertThat(result.updatedAt()).isEqualTo(NOW);
	}

	/** 혼잡도 근거가 없어도(수집 공백 등) 장소 상세는 열려야 하므로 혼잡도만 비운다. */
	@Test
	void leavesCongestionEmptyWhenThePlaceHasNoFreshObservation() {
		when(placeRepository.findOneById(PLACE_ID)).thenReturn(Optional.of(activePlace()));
		when(areaCongestionReader.atPlace(PLACE_ID)).thenReturn(Optional.empty());
		when(placeRecentReportCounter.countWithin(PLACE_ID)).thenReturn(0L);

		assertThat(service().status(PLACE_ID).congestion()).isNull();
	}

	@Test
	void rejectsUnknownPlaceAsNotFound() {
		when(placeRepository.findOneById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().status(999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	/** 비활성 장소(시드 정리 등)는 검색에도 안 나오니 직접 조회해도 없는 것과 같다. */
	@Test
	void rejectsInactivePlaceAsNotFound() {
		Place place = Place.create("POI014", "역삼역", PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.5"), new BigDecimal("127.03"), "seoul-citydata");
		org.springframework.test.util.ReflectionTestUtils.setField(place, "id", PLACE_ID);
		org.springframework.test.util.ReflectionTestUtils.setField(place, "active", false);
		when(placeRepository.findOneById(PLACE_ID)).thenReturn(Optional.of(place));

		assertThatThrownBy(() -> service().status(PLACE_ID)).isInstanceOf(BusinessException.class);
	}

	private Place activePlace() {
		Place place = Place.create("POI014", "역삼역", PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.5"), new BigDecimal("127.03"), "seoul-citydata");
		org.springframework.test.util.ReflectionTestUtils.setField(place, "id", PLACE_ID);
		return place;
	}

	private PlaceSituationQueryService service() {
		return new PlaceSituationQueryService(
				placeRepository, areaCongestionReader, placeRecentReportCounter, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
