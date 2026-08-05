package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.place.application.PlaceBoundaries;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceBoundary;
import com.ojimasam.safy.place.domain.type.PlaceCategory;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@ExtendWith(MockitoExtension.class)
class PlaceRecentReportCounterTest {
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
	private static final long PLACE_ID = 14L;

	// [경도, 위도] 사각형: (127.00,37.50) ~ (127.01,37.51).
	private static final String SQUARE_BOUNDARY = """
			{"type":"Polygon","coordinates":[[
				[127.00,37.50],[127.01,37.50],[127.01,37.51],[127.00,37.51],[127.00,37.50]
			]]}
			""";

	@Mock ReportRepository reportRepository;
	@Mock PlaceBoundaries placeBoundaries;

	/**
	 * 제보에 저장된 placeId(가장 작은 포함 장소)를 쓰지 않고, 검색한 장소의 폴리곤으로
	 * 직접 재판정한다. 그래야 관광특구처럼 넓은 장소를 검색해도 안에 있는 작은 장소(역
	 * 등)로 배정된 제보가 빠지지 않는다.
	 */
	@Test
	void countsOnlyReportsInsideTheSearchedPlacesPolygon() {
		givenBoundary();
		when(reportRepository.findByPublicVisibilityAndReportedAtAfter(any(), any())).thenReturn(List.of(
				report(new BigDecimal("37.505"), new BigDecimal("127.005")), // 폴리곤 안
				report(new BigDecimal("37.600"), new BigDecimal("127.100"))  // 폴리곤 밖
		));

		assertThat(new PlaceRecentReportCounter(reportRepository, placeBoundaries, fixedClock()).countWithin(PLACE_ID))
				.isEqualTo(1);
	}

	/** 30분 창·공개 여부 필터는 저장소 쿼리 조건으로 넘긴다. 창 시작 시각을 확인한다. */
	@Test
	void queriesFromThirtyMinutesAgo() {
		givenBoundary();
		when(reportRepository.findByPublicVisibilityAndReportedAtAfter(any(), any())).thenReturn(List.of());
		ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);

		new PlaceRecentReportCounter(reportRepository, placeBoundaries, fixedClock()).countWithin(PLACE_ID);

		org.mockito.Mockito.verify(reportRepository)
				.findByPublicVisibilityAndReportedAtAfter(
						org.mockito.ArgumentMatchers.eq(ReportPublicVisibility.PUBLISHED), since.capture());
		assertThat(since.getValue()).isEqualTo(NOW.minus(PublicReportQueryService.RECENT_WINDOW));
	}

	/** 경계를 못 읽는 장소(좌표 미확정 등)는 조회 없이 0이다. */
	@Test
	void returnsZeroWithoutQueryingWhenBoundaryIsMissing() {
		when(placeBoundaries.all()).thenReturn(List.of());

		assertThat(new PlaceRecentReportCounter(reportRepository, placeBoundaries, fixedClock()).countWithin(PLACE_ID))
				.isZero();
		verifyNoInteractions(reportRepository);
	}

	private void givenBoundary() {
		Place place = Place.create("POI014", "강남역", PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.505"), new BigDecimal("127.005"), "seoul-citydata");
		ReflectionTestUtils.setField(place, "id", PLACE_ID);
		ReflectionTestUtils.setField(place, "boundaryGeoJson", SQUARE_BOUNDARY);
		PlaceBoundary boundary = PlaceBoundary.from(place).orElseThrow();
		when(placeBoundaries.all()).thenReturn(List.of(boundary));
	}

	private Clock fixedClock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

	private Report report(BigDecimal latitude, BigDecimal longitude) {
		Report report = Report.create(1L, ReportType.IMAGE, ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION, "혼잡합니다", latitude, longitude, "서울특별시");
		ReflectionTestUtils.setField(report, "publicVisibility", ReportPublicVisibility.PUBLISHED);
		return report;
	}
}
