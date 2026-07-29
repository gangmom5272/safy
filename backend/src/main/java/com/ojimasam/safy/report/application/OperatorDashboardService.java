package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.live.application.StreamStatus;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.application.dto.DashboardImageView;
import com.ojimasam.safy.report.application.dto.DashboardLiveView;
import com.ojimasam.safy.report.application.dto.OperatorDashboardTileView;

@Service
public class OperatorDashboardService {
	private static final Duration THUMBNAIL_URL_EXPIRY = Duration.ofMinutes(10);

	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final ReportImageRepository imageRepository;
	private final LiveSessionRepository liveSessionRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final OperatorReportAccessService accessService;
	private final Clock clock;

	@Autowired
	public OperatorDashboardService(ReportOperatorAssignmentRepository assignmentRepository,
			ReportImageRepository imageRepository,
			LiveSessionRepository liveSessionRepository,
			MediaObjectStorage mediaObjectStorage,
			OperatorReportAccessService accessService) {
		this(assignmentRepository, imageRepository, liveSessionRepository, mediaObjectStorage, accessService,
				Clock.systemUTC());
	}

	OperatorDashboardService(ReportOperatorAssignmentRepository assignmentRepository,
			ReportImageRepository imageRepository,
			LiveSessionRepository liveSessionRepository,
			MediaObjectStorage mediaObjectStorage,
			OperatorReportAccessService accessService,
			Clock clock) {
		this.assignmentRepository = assignmentRepository;
		this.imageRepository = imageRepository;
		this.liveSessionRepository = liveSessionRepository;
		this.mediaObjectStorage = mediaObjectStorage;
		this.accessService = accessService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<OperatorDashboardTileView> getTiles(Long userId, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");

		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt"));
		Page<ReportOperatorAssignment> assignments;
		if (accessService.isSuperAdmin(userId)) {
			assignments = assignmentRepository.findAllByStatusIn(
					OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES, pageable);
		} else {
			accessService.requireActiveOperator(userId);
			assignments = assignmentRepository.findDashboardAssignmentsForOperator(
					userId, OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES, pageable);
		}

		List<Long> reportIds = assignments.getContent().stream()
				.map(assignment -> assignment.getReport().getId())
				.toList();
		Map<Long, List<ReportImage>> imagesByReportId = imagesByReportId(reportIds);
		Map<Long, LiveSession> liveByReportId = liveByReportId(reportIds);
		Instant thumbnailExpiresAt = clock.instant().plus(THUMBNAIL_URL_EXPIRY);
		List<OperatorDashboardTileView> tiles = assignments.getContent().stream()
				.map(assignment -> tile(assignment, imagesByReportId, liveByReportId, thumbnailExpiresAt))
				.toList();
		return new PageImpl<>(tiles, pageable, assignments.getTotalElements());
	}

	private Map<Long, List<ReportImage>> imagesByReportId(List<Long> reportIds) {
		Map<Long, List<ReportImage>> result = new HashMap<>();
		if (reportIds.isEmpty()) return result;
		for (ReportImage image : imageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(reportIds)) {
			result.computeIfAbsent(image.getReport().getId(), ignored -> new java.util.ArrayList<>()).add(image);
		}
		return result;
	}

	private Map<Long, LiveSession> liveByReportId(List<Long> reportIds) {
		if (reportIds.isEmpty()) return Map.of();
		Map<Long, LiveSession> result = new HashMap<>();
		for (LiveSession session : liveSessionRepository.findAllByReportIdIn(reportIds)) {
			result.put(session.getReportId(), session);
		}
		return result;
	}

	private OperatorDashboardTileView tile(ReportOperatorAssignment assignment,
			Map<Long, List<ReportImage>> imagesByReportId,
			Map<Long, LiveSession> liveByReportId,
			Instant thumbnailExpiresAt) {
		Report report = assignment.getReport();
		DashboardImageView image = report.getType() == ReportType.IMAGE
				? image(imagesByReportId.getOrDefault(report.getId(), List.of()), thumbnailExpiresAt)
				: null;
		LiveSession session = liveByReportId.get(report.getId());
		DashboardLiveView live = report.getType() == ReportType.LIVE && session != null
				? new DashboardLiveView(session.getId(), StreamStatus.from(session.getStatus()),
						session.getStartedAt(), session.getExpiresAt())
				: null;
		return new OperatorDashboardTileView(
				assignment.getId(), report.getId(), report.getType(), report.getCategory(), report.getSubcategory(),
				report.getDescription(), report.getLatitude(), report.getLongitude(), report.getAddress(),
				report.getStatus(), assignment.getStatus(), assignment.getRoute().getControlCenter().getId(),
				assignment.getRoute().getControlCenter().getName(), assignment.getOperator().getId(),
				assignment.getOperator().getDisplayName(), assignment.getAssignedAt(), image, live);
	}

	private DashboardImageView image(List<ReportImage> images, Instant thumbnailExpiresAt) {
		if (images.isEmpty()) {
			return new DashboardImageView(0, MediaStorageStatus.PENDING_UPLOAD, null, null);
		}
		MediaAsset asset = images.getFirst().getMediaAsset();
		String thumbnailUrl = asset.getStorageStatus() == MediaStorageStatus.AVAILABLE
				? mediaObjectStorage.createPresignedGetUrl(
						asset.getBucketName(), asset.getObjectKey(), THUMBNAIL_URL_EXPIRY)
				: null;
		return new DashboardImageView(images.size(), asset.getStorageStatus(), thumbnailUrl,
				thumbnailUrl == null ? null : thumbnailExpiresAt);
	}
}
