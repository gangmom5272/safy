package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.realtime.application.RealtimeEventService;
import com.ojimasam.safy.realtime.domain.RealtimeEventType;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCategory;
import com.ojimasam.safy.report.domain.ReportMessage;
import com.ojimasam.safy.report.domain.ReportMessageRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportSubcategory;
import com.ojimasam.safy.report.domain.ReportType;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;
import com.ojimasam.safy.user.domain.UserRole;

@ExtendWith(MockitoExtension.class)
class ReportMessageServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

	@Mock ReportRepository reportRepository;
	@Mock ReportMessageRepository messageRepository;
	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock UserRepository userRepository;
	@Mock OperatorReportAccessService operatorAccessService;
	@Mock RealtimeEventService eventService;

	private ReportMessageService service;

	@BeforeEach
	void setUp() {
		service = new ReportMessageService(reportRepository, messageRepository, assignmentRepository,
				userRepository, operatorAccessService, eventService, Clock.fixed(NOW, ZoneOffset.UTC));
		when(messageRepository.save(any())).thenAnswer(invocation -> {
			ReportMessage message = invocation.getArgument(0);
			ReflectionTestUtils.setField(message, "id", 100L);
			return message;
		});
	}

	@Test
	void operatorMessageIsPersistedAndNotifiesReporter() {
		Report report = report();
		User operator = mock(User.class);
		when(operator.getRole()).thenReturn(UserRole.OPERATOR);
		when(reportRepository.findOneById(10L)).thenReturn(Optional.of(report));
		when(userRepository.findById(30L)).thenReturn(Optional.of(operator));

		ReportMessageResponse response = service.sendFromOperator(30L, 10L, "화면을 더 높게 보여주세요.");

		assertThat(response.content()).isEqualTo("화면을 더 높게 보여주세요.");
		verify(operatorAccessService).requireAccess(30L, 10L);
		verify(eventService).record(1L, RealtimeEventType.REPORT_MESSAGE, 10L, null, 100L);
	}

	@Test
	void citizenMessageNotifiesAssignedOperator() {
		Report report = report();
		User operator = mock(User.class);
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		when(operator.getId()).thenReturn(30L);
		when(assignment.getOperator()).thenReturn(operator);
		when(reportRepository.findByIdAndReporterId(10L, 1L)).thenReturn(Optional.of(report));
		when(assignmentRepository.findByReport_IdAndStatusIn(
				org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(Optional.of(assignment));

		service.sendFromCitizen(1L, 10L, "현재 출구 쪽을 촬영 중입니다.");

		verify(eventService).record(30L, RealtimeEventType.REPORT_MESSAGE, 10L, null, 100L);
	}

	private Report report() {
		Report report = Report.create(1L, ReportType.LIVE,
				ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION,
				"출구 혼잡", new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		ReflectionTestUtils.setField(report, "id", 10L);
		return report;
	}
}
