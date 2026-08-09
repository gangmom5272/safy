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
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.analysis.domain.type.RiskAssessmentStatus;
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
	@Mock com.ojimasam.safy.report.domain.ReportRepository reportRepository;
	@Mock com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository membershipRepository;
	@Mock RiskAssessmentRepository riskAssessmentRepository;

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
		RiskAssessment risk = mock(RiskAssessment.class);
		when(risk.getReportId()).thenReturn(11L);
		when(risk.getAssessmentStatus()).thenReturn(RiskAssessmentStatus.COMPLETED);
		when(risk.getFinalRiskScore()).thenReturn(84.5);
		when(risk.getRiskLevel()).thenReturn(RiskLevel.VERY_HIGH);
		when(riskAssessmentRepository.findAllByReportIdIn(List.of(10L, 11L))).thenReturn(List.of(risk));
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
		assertThat(liveTile.risk().score()).isEqualTo(84.5);
		assertThat(liveTile.risk().level()).isEqualTo(RiskLevel.VERY_HIGH);
		verify(accessService).requireActiveOperator(30L);
	}

	@Test
	void showsRepresentativeOriginalFrameOnLiveTile() {
		Report liveReport = report(11L, ReportType.LIVE, "라이브 제보");
		withRepresentativeFrame(liveReport, "frames/11/live/token/000012.jpg");
		ReportOperatorAssignment liveAssignment = assignment(101L, liveReport);
		when(accessService.isSuperAdmin(30L)).thenReturn(false);
		when(assignmentRepository.findDashboardAssignmentsForOperator(eq(30L), any(), any()))
				.thenReturn(new PageImpl<>(List.of(liveAssignment)));
		when(imageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(11L)))
				.thenReturn(List.of());
		when(liveSessionRepository.findAllByReportIdIn(List.of(11L))).thenReturn(List.of());
		when(riskAssessmentRepository.findAllByReportIdIn(List.of(11L))).thenReturn(List.of());
		when(mediaObjectStorage.createPresignedGetUrl("safy", "frames/11/live/token/000012.jpg",
				java.time.Duration.ofMinutes(10))).thenReturn("https://storage/frame-thumbnail");

		var result = service().getTiles(30L, 0, 25);

		OperatorDashboardTileView liveTile = result.getContent().getFirst();
		assertThat(liveTile.image().imageCount()).isEqualTo(1);
		assertThat(liveTile.image().status()).isEqualTo(MediaStorageStatus.AVAILABLE);
		assertThat(liveTile.image().thumbnailUrl()).isEqualTo("https://storage/frame-thumbnail");
		assertThat(liveTile.image().thumbnailUrlExpiresAt()).isEqualTo(NOW.plusSeconds(600));
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

	@Test
	void queueReturnsUnassignedReportsOfMyCenter() {
		Report queued = report(10L, ReportType.IMAGE, "미배정 제보");
		var membership = mock(com.ojimasam.safy.admin.domain.AdminCenterMembership.class);
		when(membership.getStatus()).thenReturn(com.ojimasam.safy.admin.domain.type.MembershipStatus.ACTIVE);
		when(membership.getControlCenterId()).thenReturn(20L);
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(java.util.Optional.of(membership));
		when(reportRepository.findCenterQueue(any(), any(), eq(20L), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(queued)));
		when(imageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(List.of(10L)))
				.thenReturn(List.of());
		when(liveSessionRepository.findAllByReportIdIn(List.of(10L))).thenReturn(List.of());

		var result = service().getQueue(30L, 0, 25);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().reportId()).isEqualTo(10L);
		assertThat(result.getContent().getFirst().image().imageCount()).isZero();
	}

	@Test
	void queueDeniesOperatorWithoutActiveMembership() {
		when(membershipRepository.findByUserIdAndIsPrimaryTrue(30L)).thenReturn(java.util.Optional.empty());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().getQueue(30L, 0, 25))
				.isInstanceOf(com.ojimasam.safy.common.exception.BusinessException.class);
	}

	private OperatorDashboardService service() {
		return new OperatorDashboardService(assignmentRepository, imageRepository, liveSessionRepository,
				mediaObjectStorage, accessService, reportRepository, membershipRepository,
				riskAssessmentRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Report report(Long id, ReportType type, String description) {
		Report report = mock(Report.class);
		when(report.getId()).thenReturn(id);
		when(report.getType()).thenReturn(type);
		when(report.getDescription()).thenReturn(description);
		return report;
	}

	/** LIVE 제보의 대표 블러본과 그 블러본이 파생된 원본 프레임을 연결한다. */
	private void withRepresentativeFrame(Report report, String frameObjectKey) {
		MediaAsset blurred = mock(MediaAsset.class);
		MediaAsset frame = mock(MediaAsset.class);
		when(report.getRepresentativeBlurAsset()).thenReturn(blurred);
		when(blurred.getSourceMediaAsset()).thenReturn(frame);
		when(frame.getStorageStatus()).thenReturn(MediaStorageStatus.AVAILABLE);
		when(frame.getBucketName()).thenReturn("safy");
		when(frame.getObjectKey()).thenReturn(frameObjectKey);
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
