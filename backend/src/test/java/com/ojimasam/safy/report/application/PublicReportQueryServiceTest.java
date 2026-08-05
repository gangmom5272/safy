package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;
import com.ojimasam.safy.report.application.dto.PublicReportView;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@ExtendWith(MockitoExtension.class)
class PublicReportQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");

	@Mock ReportRepository reportRepository;
	@Mock MediaObjectStorage mediaObjectStorage;
	@Mock ReportAnalysisQueryService reportAnalysisQueryService;

	@Test
	void trimsExtraRowAndReportsNextCursor() {
		Report first = report(1L, NOW.minusSeconds(60));
		Report second = report(2L, NOW.minusSeconds(120));
		Report overflow = report(3L, NOW.minusSeconds(180));
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				eq(ReportPublicVisibility.PUBLISHED), any(), any(), any()))
				.thenReturn(List.of(first, second, overflow));

		PublicReportCursorResponse result = service().reports(null, 2);

		assertThat(result.content()).extracting(PublicReportView::reportId).containsExactly(1L, 2L);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.nextCursor()).isEqualTo(NOW.minusSeconds(120));
	}

	@Test
	void marksLastPageWhenNoOverflowRow() {
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				eq(ReportPublicVisibility.PUBLISHED), any(), any(), any()))
				.thenReturn(List.of(report(1L, NOW.minusSeconds(60))));

		PublicReportCursorResponse result = service().reports(null, 20);

		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
	}

	/** 커서를 생략하면 최신부터 읽어야 하므로 상한 센티넬로 조회한다. */
	@Test
	void missingCursorReadsFromTheNewestRow() {
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				eq(ReportPublicVisibility.PUBLISHED), any(), any(), any()))
				.thenReturn(List.of());
		ArgumentCaptor<Instant> cursor = ArgumentCaptor.forClass(Instant.class);

		service().reports(null, 20);

		org.mockito.Mockito.verify(reportRepository)
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						eq(ReportPublicVisibility.PUBLISHED), any(), cursor.capture(), any(Pageable.class));
		assertThat(cursor.getValue()).isAfter(NOW);
	}

	/** 마커로 보일 창을 30분으로 자른다. 창이 넓어지면 지도에 오래된 제보가 남는다. */
	@Test
	void listsOnlyReportsInsideTheRecentWindow() {
		when(reportRepository.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				eq(ReportPublicVisibility.PUBLISHED), any(), any(), any()))
				.thenReturn(List.of());
		ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);

		service().reports(null, 20);

		org.mockito.Mockito.verify(reportRepository)
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						eq(ReportPublicVisibility.PUBLISHED), from.capture(), any(), any(Pageable.class));
		assertThat(from.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
	}

	/** 상황 요약의 '최근 제보' 수는 목록과 같은 조건이어야 한다. */
	@Test
	void recentAreaCountUsesPublishedVisibilityAndSameWindow() {
		when(reportRepository.countByAdministrativeArea_IdAndPublicVisibilityAndReportedAtAfter(
				7L, ReportPublicVisibility.PUBLISHED, NOW.minus(Duration.ofMinutes(30))))
				.thenReturn(3L);

		assertThat(service().countRecentByArea(7L)).isEqualTo(3L);
	}

	@Test
	void rejectsPageSizeOutOfRange() {
		assertThatThrownBy(() -> service().reports(null, 0))
				.isInstanceOf(BusinessException.class)
				.hasMessage("size는 1 이상 100 이하여야 합니다.");
		assertThatThrownBy(() -> service().reports(null, 101))
				.isInstanceOf(BusinessException.class)
				.hasMessage("size는 1 이상 100 이하여야 합니다.");
	}

	@Test
	void areaFeedFiltersByAreaAndPublishedVisibility() {
		when(reportRepository
				.findByAdministrativeArea_IdAndPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						eq(7L), eq(ReportPublicVisibility.PUBLISHED), any(), any(), any()))
				.thenReturn(List.of(report(1L, NOW.minusSeconds(60))));

		PublicReportCursorResponse result = service().reportsByArea(7L, NOW, 20);

		assertThat(result.content()).extracting(PublicReportView::reportId).containsExactly(1L);
	}

	@Test
	void detailHidesUnpublishedReportAsNotFound() {
		when(reportRepository.findByIdAndPublicVisibility(42L, ReportPublicVisibility.PUBLISHED))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().report(42L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	/** 블러본이 없으면 원본을 대신 내보내지 않고 이미지 없이 응답한다. */
	@Test
	void omitsImageWhenBlurredAssetIsMissing() {
		when(reportRepository.findByIdAndPublicVisibility(42L, ReportPublicVisibility.PUBLISHED))
				.thenReturn(Optional.of(report(42L, NOW.minusSeconds(60))));

		PublicReportView result = service().report(42L);

		assertThat(result.blurredImageUrl()).isNull();
		assertThat(result.blurredImageExpiresAt()).isNull();
		verifyNoInteractions(mediaObjectStorage);
	}

	@Test
	void presignsBlurredAssetWhenPresent() {
		Report report = report(42L, NOW.minusSeconds(60));
		MediaAsset blurred = MediaAsset.prepareOriginal(
				1L, 42L, "safy", "reports/42/blurred.jpg", "blurred.jpg", "image/jpeg", NOW.plusSeconds(300));
		blurred.markAvailable(2048L);
		ReflectionTestUtils.setField(report, "representativeBlurAsset", blurred);
		when(reportRepository.findByIdAndPublicVisibility(42L, ReportPublicVisibility.PUBLISHED))
				.thenReturn(Optional.of(report));
		when(mediaObjectStorage.createPresignedGetUrl("safy", "reports/42/blurred.jpg", Duration.ofMinutes(10)))
				.thenReturn("http://localhost:9000/blurred");

		PublicReportView result = service().report(42L);

		assertThat(result.blurredImageUrl()).isEqualTo("http://localhost:9000/blurred");
		assertThat(result.blurredImageExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
	}

	private PublicReportQueryService service() {
		return new PublicReportQueryService(reportRepository, mediaObjectStorage,
				reportAnalysisQueryService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Report report(Long id, Instant reportedAt) {
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION, "출구 혼잡",
				new BigDecimal("37.5"), new BigDecimal("127.0"), "서울특별시 중구");
		ReflectionTestUtils.setField(report, "id", id);
		ReflectionTestUtils.setField(report, "reportedAt", reportedAt);
		return report;
	}
}
