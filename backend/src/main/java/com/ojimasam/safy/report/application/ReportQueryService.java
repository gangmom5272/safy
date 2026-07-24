package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import com.ojimasam.safy.media.domain.MediaStorageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportStatusHistoryRepository;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;

@Service
public class ReportQueryService {
	private static final Duration PREVIEW_URL_EXPIRY = Duration.ofMinutes(10);

	private final ReportRepository reportRepository;
	private final ReportImageRepository reportImageRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportStatusHistoryRepository historyRepository;
	private final Clock clock;

	@Autowired
	public ReportQueryService(
			ReportRepository reports,
			ReportImageRepository images,
			MediaObjectStorage storage,
			ReportCenterRouteRepository routeRepository,
			ReportStatusHistoryRepository historyRepository
	) {
		this(reports, images, storage, routeRepository, historyRepository, Clock.systemUTC());
	}

	ReportQueryService(ReportRepository reports, ReportImageRepository images, MediaObjectStorage storage,
			ReportCenterRouteRepository routeRepository, ReportStatusHistoryRepository historyRepository, Clock clock) {
		this.reportRepository = reports;
		this.reportImageRepository = images;
		this.mediaObjectStorage = storage;
		this.routeRepository = routeRepository;
		this.historyRepository = historyRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReportDetailView getMine(Long reporterId, Long reportId) {
		Report report = reportRepository.findByIdAndReporterId(reportId, reporterId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
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
				report.getId(), report.getType(), report.getDescription(), report.getLatitude(), report.getLongitude(),
				report.getAddress(), report.getStatus(), report.getRoutingStatus(),
				area == null ? null : area.getId(), area == null ? null : area.getCode(), area == null ? null : area.getName(),
				assignedCenter,
				report.getReportedAt(), report.getCreatedAt(), report.getUpdatedAt(), images
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
					report.getId(), report.getType(), report.getDescription(), report.getAddress(), report.getStatus(),
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
