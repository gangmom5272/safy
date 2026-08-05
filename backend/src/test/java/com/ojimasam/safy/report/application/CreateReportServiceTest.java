package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.grid.application.GridCellRegistry;
import com.ojimasam.safy.place.application.PlaceLocator;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.application.dto.CreateReportCommand;
import com.ojimasam.safy.report.application.dto.CreateReportResult;
import com.ojimasam.safy.report.application.event.ReportRoutingRequestedEvent;

@ExtendWith(MockitoExtension.class)
class CreateReportServiceTest {
	@Mock ReportRepository reportRepository;
	@Mock GridCellRegistry gridCellRegistry;
	@Mock PlaceLocator placeLocator;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock ReportRealtimeNotifier realtimeNotifier;
	@InjectMocks CreateReportService service;

	@Test
	void createsLiveReportAndRequestsRouting() {
		givenSavedReportGetsId(42L);
		when(placeLocator.locate(any(), any())).thenReturn(Optional.empty());

		CreateReportResult result = service.create(command());

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(ReportType.LIVE);
		assertThat(captor.getValue().getCategory()).isEqualTo(ReportCategory.CROWD_CONGESTION);
		assertThat(captor.getValue().getSubcategory()).isEqualTo(ReportSubcategory.EXIT_CONGESTION);
		assertThat(captor.getValue().getDescription()).isEqualTo("출구 주변이 혼잡합니다.");
		assertThat(result.reportId()).isEqualTo(42L);
		assertThat(result.status()).isEqualTo(ReportStatus.RECEIVED);
		verify(eventPublisher).publishEvent(any(ReportRoutingRequestedEvent.class));
		verify(realtimeNotifier).reportReceived(42L, 1L);
	}

	@Test
	void assignsGridCellCodeAndPlaceOnCreate() {
		givenSavedReportGetsId(43L);
		when(placeLocator.locate(any(), any())).thenReturn(Optional.of(96L));

		service.create(command());

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(captor.capture());
		// 격자 셀은 좌표에서 바로 나온다.
		assertThat(captor.getValue().getGridCellCode()).isEqualTo("S100-r184-c246");
		assertThat(captor.getValue().getPlaceId()).isEqualTo(96L);
		// 셀 행도 함께 확보한다. 격자 위험도가 셀을 참조하려면 행이 있어야 한다.
		verify(gridCellRegistry).findOrCreate(
				new BigDecimal("37.5665000"), new BigDecimal("126.9780000")
		);
	}

	@Test
	void leavesPlaceEmptyWhenCoordinateIsOutsideEveryPlace() {
		givenSavedReportGetsId(44L);
		when(placeLocator.locate(any(), any())).thenReturn(Optional.empty());

		service.create(command());

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(captor.capture());
		assertThat(captor.getValue().getPlaceId()).isNull();
		// 장소를 못 찾아도 격자 셀은 채워진다.
		assertThat(captor.getValue().getGridCellCode()).isEqualTo("S100-r184-c246");
	}

	private void givenSavedReportGetsId(long id) {
		when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", id);
			return report;
		});
	}

	private static CreateReportCommand command() {
		return new CreateReportCommand(
				1L, ReportType.LIVE, ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION,
				"출구 주변이 혼잡합니다.",
				new BigDecimal("37.5665000"), new BigDecimal("126.9780000")
		);
	}
}
