package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@ExtendWith(MockitoExtension.class)
class RadiusRecentReportCounterTest {
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
	private static final BigDecimal LATITUDE = new BigDecimal("37.5000000");
	private static final BigDecimal LONGITUDE = new BigDecimal("127.0000000");
	private static final double RADIUS_METERS = 1_000;

	@Mock ReportRepository reportRepository;

	/**
	 * bbox 는 원보다 넓다. 사각형만 쓰면 모서리 쪽 제보가 반경보다 최대 41% 멀어도
	 * 포함되므로, 통과한 결과를 실제 거리로 다시 걸러야 한다.
	 */
	@Test
	void excludesReportsInsideBoundingBoxButOutsideTheCircle() {
		// 정북 약 500m — 원 안
		Report inside = report("37.5045000", "127.0000000");
		// 북동 대각선. 위도·경도 각각 약 800m 라 직선거리는 약 1.13km 로 원 밖이지만
		// bbox 로는 통과한다.
		Report outsideCircle = report("37.5072000", "127.0091000");
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
				any(), any(), any(), any(), any(), any()))
				.thenReturn(List.of(inside, outsideCircle));

		assertThat(counter().countWithin(LATITUDE, LONGITUDE, RADIUS_METERS)).isEqualTo(1);
	}

	/** 승인 여부와 30분 창은 저장소 조건으로 넘긴다. */
	@Test
	void queriesPublishedReportsFromThirtyMinutesAgo() {
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
				any(), any(), any(), any(), any(), any()))
				.thenReturn(List.of());
		ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);

		counter().countWithin(LATITUDE, LONGITUDE, RADIUS_METERS);

		org.mockito.Mockito.verify(reportRepository)
				.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
						eq(ReportPublicVisibility.PUBLISHED), since.capture(), any(), any(), any(), any());
		assertThat(since.getValue()).isEqualTo(NOW.minus(PublicReportQueryService.RECENT_WINDOW));
	}

	/** bbox 는 반경을 감싸야 한다 — 좁으면 원 안 제보가 1차 필터에서 빠진다. */
	@Test
	void boundingBoxEnclosesTheRadius() {
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
				any(), any(), any(), any(), any(), any()))
				.thenReturn(List.of());
		ArgumentCaptor<BigDecimal> bounds = ArgumentCaptor.forClass(BigDecimal.class);

		counter().countWithin(LATITUDE, LONGITUDE, RADIUS_METERS);

		org.mockito.Mockito.verify(reportRepository)
				.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
						any(), any(), bounds.capture(), bounds.capture(), bounds.capture(), bounds.capture());
		List<BigDecimal> captured = bounds.getAllValues();
		double minLatitude = captured.get(0).doubleValue();
		double maxLatitude = captured.get(1).doubleValue();
		// 1km 는 위도 약 0.009도다. 정북 1km 지점이 bbox 안에 들어와야 한다.
		assertThat(maxLatitude - minLatitude).isGreaterThan(0.017);
		assertThat(minLatitude).isLessThan(37.5);
		assertThat(maxLatitude).isGreaterThan(37.5);
	}

	@Test
	void returnsZeroWithoutQueryingWhenCoordinateIsMissing() {
		assertThat(counter().countWithin(null, LONGITUDE, RADIUS_METERS)).isZero();
		assertThat(counter().countWithin(LATITUDE, null, RADIUS_METERS)).isZero();
		assertThat(counter().countWithin(LATITUDE, LONGITUDE, 0)).isZero();

		verifyNoInteractions(reportRepository);
	}

	private RadiusRecentReportCounter counter() {
		return new RadiusRecentReportCounter(reportRepository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Report report(String latitude, String longitude) {
		Report report = Report.create(1L, ReportType.IMAGE, ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION, "혼잡합니다",
				new BigDecimal(latitude), new BigDecimal(longitude), "서울특별시");
		ReflectionTestUtils.setField(report, "publicVisibility", ReportPublicVisibility.PUBLISHED);
		return report;
	}
}
