package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.live.application.StreamStatus;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.report.application.dto.OperatorDashboardTileView;

@ExtendWith(MockitoExtension.class)
class OperatorDashboardServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

	@Mock ReportOperatorAssignmentRepository assignmentRepository;
	@Mock ReportImageRepository imageRepository;
	@Mock LiveSessionRepository liveSessionRepository;
	@Mock MediaObjectStorage mediaObjectStorage;
	@Mock OperatorReportAccessService accessService;

	@Test
	void returnsImageAndLiveTilesInOnePageForAssignedOperator() {
		Report imageReport = report(10L, ReportType.IMAGE, "이미지 제보");
		Report liveReport = report(11L, ReportType.LIVE, "라이브 제보");
		ReportOperatorAssignment imageAssignment = assignment(100L, imageReport);
		ReportOperatorAssignment liveAssignment = assignment(101L, liveReport);
		ReportImage image = image(imageReport);
		LiveSession liveSession = liveSession(liveReport);
		when(accessService.isSuperAdmin(30L)).thenReturn(false);
		when(assignmentRepository.findDashboardAssignmentsForOperator(eq(30L), any(), any()))
				.thenReturn(new PageImpl<>(List.of(imageAssignment, liveAssignment)));
		when(imageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(10L, 11L)))
				.thenReturn(List.of(image));
		when(liveSessionRepository.findAllByReportIdIn(List.of(10L, 11L)))
				.thenReturn(List.of(liveSession));
		when(mediaObjectStorage.createPresignedGetUrl("safy", "reports/10/image.png",
				java.time.Duration.ofMinutes(10))).thenReturn("https://storage/thumbnail");

		var result = service().getTiles(30L, 0, 25);

		assertThat(result.getContent()).hasSize(2);
		OperatorDashboardTileView imageTile = result.getContent().get(0);
		assertThat(imageTile.reportType()).isEqualTo(ReportType.IMAGE);
		assertThat(imageTile.image().imageCount()).isEqualTo(1);
		assertThat(imageTile.image().status()).isEqualTo(MediaStorageStatus.AVAILABLE);
		assertThat(imageTile.image().thumbnailUrl()).isEqualTo("https://storage/thumbnail");
		assertThat(imageTile.image().thumbnailUrlExpiresAt()).isEqualTo(NOW.plusSeconds(600));
		assertThat(imageTile.live()).isNull();
		OperatorDashboardTileView liveTile = result.getContent().get(1);
		assertThat(liveTile.reportType()).isEqualTo(ReportType.LIVE);
		assertThat(liveTile.image()).isNull();
		assertThat(liveTile.live().streamRoomId()).isEqualTo(liveSession.getId());
		assertThat(liveTile.live().status()).isEqualTo(StreamStatus.LIVE);
		verify(accessService).requireActiveOperator(30L);
	}

	@Test
	void superAdminGetsAllActiveAssignments() {
		when(accessService.isSuperAdmin(1L)).thenReturn(true);
		when(assignmentRepository.findAllByStatusIn(any(), any())).thenReturn(new PageImpl<>(List.of()));

		var result = service().getTiles(1L, 0, 25);

		assertThat(result).isEmpty();
		verify(assignmentRepository).findAllByStatusIn(
				eq(OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES), any());
	}

	private OperatorDashboardService service() {
		return new OperatorDashboardService(assignmentRepository, imageRepository, liveSessionRepository,
				mediaObjectStorage, accessService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Report report(Long id, ReportType type, String description) {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(id);
		when(report.getType()).thenReturn(type);
		when(report.getDescription()).thenReturn(description);
		return report;
	}

	private ReportOperatorAssignment assignment(Long id, Report report) {
		ReportOperatorAssignment assignment = mock(ReportOperatorAssignment.class);
		ReportCenterRoute route = mock(ReportCenterRoute.class);
		ControlCenter center = mock(ControlCenter.class);
		User operator = mock(User.class);
		when(assignment.getId()).thenReturn(id);
		when(assignment.getReport()).thenReturn(report);
		when(assignment.getStatus()).thenReturn(ReportOperatorAssignmentStatus.ASSIGNED);
		when(assignment.getRoute()).thenReturn(route);
		when(assignment.getOperator()).thenReturn(operator);
		when(route.getControlCenter()).thenReturn(center);
		when(center.getId()).thenReturn(20L);
		when(center.getName()).thenReturn("강남 관제센터");
		when(operator.getId()).thenReturn(30L);
		when(operator.getDisplayName()).thenReturn("강남 담당자");
		when(assignment.getAssignedAt()).thenReturn(NOW);
		return assignment;
	}

	private ReportImage image(Report report) {
		ReportImage image = mock(ReportImage.class);
		MediaAsset asset = mock(MediaAsset.class);
		when(image.getReport()).thenReturn(report);
		when(image.getMediaAsset()).thenReturn(asset);
		when(asset.getStorageStatus()).thenReturn(MediaStorageStatus.AVAILABLE);
		when(asset.getBucketName()).thenReturn("safy");
		when(asset.getObjectKey()).thenReturn("reports/10/image.png");
		return image;
	}

	private LiveSession liveSession(Report report) {
		LiveSession session = mock(LiveSession.class);
		Long reportId = report.getId();
		when(session.getReportId()).thenReturn(reportId);
		when(session.getId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000011"));
		when(session.getStatus()).thenReturn(LiveSessionStatus.ACTIVE);
		when(session.getStartedAt()).thenReturn(NOW);
		when(session.getExpiresAt()).thenReturn(NOW.plusSeconds(3600));
		return session;
	}
}
