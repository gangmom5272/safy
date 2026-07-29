package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.ReportOperatorAssignmentRepository;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.report.application.dto.AssignedControlCenterView;
import com.ojimasam.safy.report.application.dto.ReportDetailView;
import com.ojimasam.safy.report.application.dto.ReportImageView;
import com.ojimasam.safy.report.application.dto.ReportListItemView;
import com.ojimasam.safy.report.application.dto.ReportStatusHistoryResponse;

@Service
public class ReportQueryService {
	private static final Duration PREVIEW_URL_EXPIRY = Duration.ofMinutes(10);

	private final ReportRepository reportRepository;
	private final ReportImageRepository reportImageRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final OperatorReportAccessService operatorAccessService;
	private final ReportOperatorAssignmentRepository assignmentRepository;
	private final Clock clock;

	@Autowired
	public ReportQueryService(
			ReportRepository reports,
			ReportImageRepository images,
			MediaObjectStorage storage,
			ReportCenterRouteRepository routeRepository,
			ReportStatusHistoryRepository historyRepository,
			OperatorReportAccessService operatorAccessService,
			ReportOperatorAssignmentRepository assignmentRepository
	) {
		this(reports, images, storage, routeRepository, historyRepository, operatorAccessService,
				assignmentRepository, Clock.systemUTC());
	}

	ReportQueryService(ReportRepository reports, ReportImageRepository images, MediaObjectStorage storage,
			ReportCenterRouteRepository routeRepository, ReportStatusHistoryRepository historyRepository,
			OperatorReportAccessService operatorAccessService,
			ReportOperatorAssignmentRepository assignmentRepository, Clock clock) {
		this.reportRepository = reports;
		this.reportImageRepository = images;
		this.mediaObjectStorage = storage;
		this.routeRepository = routeRepository;
		this.historyRepository = historyRepository;
		this.operatorAccessService = operatorAccessService;
		this.assignmentRepository = assignmentRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReportDetailView getMine(Long reporterId, Long reportId) {
		Report report = reportRepository.findByIdAndReporterId(reportId, reporterId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return detail(report);
	}

	@Transactional(readOnly = true)
	public ReportDetailView getForOperator(Long operatorId, Long reportId) {
		operatorAccessService.requireAccess(operatorId, reportId);
		Report report = reportRepository.findOneById(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return detail(report, myAssignmentStatus(operatorId, reportId));
	}

	/** 재배정 이력이 있으면 배정이 여러 건 남으므로 가장 최근 것을 내 처리 결과로 본다. */
	private ReportOperatorAssignmentStatus myAssignmentStatus(Long operatorId, Long reportId) {
		return assignmentRepository.findAllByReport_IdAndOperator_Id(reportId, operatorId).stream()
				.max(Comparator.comparing(ReportOperatorAssignment::getAssignedAt))
				.map(ReportOperatorAssignment::getStatus)
				.orElse(null);
	}

	private ReportDetailView detail(Report report) {
		return detail(report, null);
	}

	private ReportDetailView detail(Report report, ReportOperatorAssignmentStatus myAssignmentStatus) {
		Long reportId = report.getId();
		Instant expiresAt = clock.instant().plus(PREVIEW_URL_EXPIRY);
		List<ReportImageView> images = reportImageRepository.findAllByReportIdOrderByDisplayOrderAsc(reportId)
				.stream().map(image -> toImageView(image, expiresAt)).toList();
		AdministrativeArea area = report.getAdministrativeArea();
		var currentRoute = routeRepository.findByReport_IdAndStatus(reportId, ReportCenterRouteStatus.ACTIVE)
				.or(() -> routeRepository.findTopByReport_IdOrderByRoutedAtDesc(reportId));
		AssignedControlCenterView assignedCenter = currentRoute
				.map(route -> AssignedControlCenterView.of(
						route.getControlCenter(),
						area == null ? null : area.getName()
				))
				.orElse(null);
		return new ReportDetailView(
				report.getId(), report.getType(), report.getCategory(), report.getSubcategory(),
				report.getDescription(), report.getLatitude(), report.getLongitude(),
				report.getAddress(), report.getStatus(), report.getRoutingStatus(),
				area == null ? null : area.getId(), area == null ? null : area.getCode(), area == null ? null : area.getName(),
				assignedCenter,
				report.getReportedAt(), report.getCreatedAt(), report.getUpdatedAt(), images,
				myAssignmentStatus
		);
	}

	@Transactional(readOnly = true)
	public List<ReportStatusHistoryResponse> getMyStatusHistory(Long reporterId, Long reportId) {
		reportRepository.findByIdAndReporterId(reportId, reporterId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return historyRepository.findAllByReport_IdOrderByChangedAtAscIdAsc(reportId)
				.stream()
				.map(ReportStatusHistoryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public Page<ReportListItemView> getMyList(Long reporterId, int page, int size) {
		if (page < 0) throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reportedAt"));
		Page<Report> reports = reportRepository.findAllByReporterId(reporterId, pageable);
		List<Long> reportIds = reports.getContent().stream().map(Report::getId).toList();
		Map<Long, List<ReportImage>> imagesByReportId = new HashMap<>();
		if (!reportIds.isEmpty()) {
			for (ReportImage image : reportImageRepository.findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(reportIds)) {
				imagesByReportId.computeIfAbsent(image.getReport().getId(), ignored -> new ArrayList<>()).add(image);
			}
		}

		Instant expiresAt = clock.instant().plus(PREVIEW_URL_EXPIRY);
		List<ReportListItemView> content = reports.getContent().stream().map(report -> {
			List<ReportImage> images = imagesByReportId.getOrDefault(report.getId(), List.of());
			ReportImageView representative = images.isEmpty() ? null : toImageView(images.getFirst(), expiresAt);
			AdministrativeArea area = report.getAdministrativeArea();
			return new ReportListItemView(
					report.getId(), report.getType(), report.getCategory(), report.getSubcategory(),
					report.getDescription(), report.getAddress(), report.getStatus(),
					report.getRoutingStatus(), area == null ? null : area.getName(), report.getReportedAt(),
					images.size(), representative
			);
		}).toList();
		return new PageImpl<>(content, pageable, reports.getTotalElements());
	}

	private ReportImageView toImageView(ReportImage reportImage, Instant expiresAt) {
		MediaAsset asset = reportImage.getMediaAsset();
		String previewUrl = asset.getStorageStatus() == MediaStorageStatus.AVAILABLE
				? mediaObjectStorage.createPresignedGetUrl(asset.getBucketName(), asset.getObjectKey(), PREVIEW_URL_EXPIRY)
				: null;
		return new ReportImageView(
				reportImage.getId(), asset.getId(), reportImage.getDisplayOrder(), asset.getOriginalFilename(),
				asset.getContentType(), asset.getFileSize(), asset.getStorageStatus(), previewUrl,
				previewUrl == null ? null : expiresAt
		);
	}
}
