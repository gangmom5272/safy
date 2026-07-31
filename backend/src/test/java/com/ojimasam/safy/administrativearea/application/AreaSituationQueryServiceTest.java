package com.ojimasam.safy.administrativearea.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.administrativearea.application.dto.AreaMapResponse;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.report.application.PublicReportQueryService;
import com.ojimasam.safy.report.application.ReportFeedQueryService;
import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;

@ExtendWith(MockitoExtension.class)
class AreaSituationQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");

	@Mock AdministrativeAreaRepository administrativeAreaRepository;
	@Mock ReportFeedQueryService reportFeedQueryService;
	@Mock PublicReportQueryService publicReportQueryService;

	/** 지도에 실리는 제보 목록은 시민 화면용이므로 공개된 건만 담는 경로에서 와야 한다. */
	@Test
	void mapSourcesReportsFromPublicFeed() {
		PublicReportCursorResponse published = new PublicReportCursorResponse(List.of(), null, false);
		givenActiveArea();
		when(publicReportQueryService.reportsByArea(7L, NOW, 20)).thenReturn(published);

		AreaMapResponse result = service().map(7L, NOW, 20);

		assertThat(result.reports()).isSameAs(published);
		verify(publicReportQueryService).reportsByArea(7L, NOW, 20);
	}

	/** 위험도 집계는 공개 여부와 무관하게 진행 중인 제보 수를 센다. */
	@Test
	void statusCountsUnpublishedReportsToo() {
		givenActiveArea();
		when(reportFeedQueryService.countActiveReports(7L)).thenReturn(4L);
		when(reportFeedQueryService.countActiveLiveReports(7L)).thenReturn(3L);

		var result = service().status(7L);

		assertThat(result.activeReportCount()).isEqualTo(4L);
		assertThat(result.liveReportCount()).isEqualTo(3L);
		assertThat(result.riskLevel()).isEqualTo("HIGH");
		verifyNoInteractions(publicReportQueryService);
	}

	private void givenActiveArea() {
		AdministrativeArea area = mock(AdministrativeArea.class);
		when(administrativeAreaRepository.findByIdAndStatus(7L, AdministrativeAreaStatus.ACTIVE))
				.thenReturn(Optional.of(area));
	}

	private AreaSituationQueryService service() {
		return new AreaSituationQueryService(administrativeAreaRepository, reportFeedQueryService,
				publicReportQueryService, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
