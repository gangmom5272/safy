package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;
import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;
import com.ojimasam.safy.report.application.dto.PublicReportView;
import com.ojimasam.safy.report.application.dto.ReportAnalysisResponse;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;

/**
 * 시민에게 공개된 제보만 조회한다.
 *
 * <p>라이브 세션 저장소를 일부러 주입받지 않는다. 시민 응답에 스트림 정보가 섞이는 것을 DTO 필드
 * 관리가 아니라 의존성 부재로 막기 위해서다. 같은 이유로 원본 이미지가 아닌
 * {@code representativeBlurAsset}(블러 파생본)만 presign 한다.
 */
@Service
@Transactional(readOnly = true)
public class PublicReportQueryService {
	private static final Duration IMAGE_URL_EXPIRY = Duration.ofMinutes(10);
	private static final Instant MAX_CURSOR = Instant.parse("9999-12-31T23:59:59.999999Z");

	private final ReportRepository reportRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final Clock clock;

	@Autowired
	public PublicReportQueryService(ReportRepository reportRepository, MediaObjectStorage mediaObjectStorage) {
		this(reportRepository, mediaObjectStorage, Clock.systemUTC());
	}

	PublicReportQueryService(ReportRepository reportRepository, MediaObjectStorage mediaObjectStorage, Clock clock) {
		this.reportRepository = reportRepository;
		this.mediaObjectStorage = mediaObjectStorage;
		this.clock = clock;
	}

	public PublicReportCursorResponse reports(Instant cursor, int size) {
		List<Report> reports = reportRepository.findByPublicVisibilityAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				ReportPublicVisibility.PUBLISHED,
				cursorOrMax(cursor),
				PageRequest.of(0, checkedPageSize(size) + 1)
		);
		return cursorResponse(reports, size);
	}

	public PublicReportCursorResponse reportsByArea(Long areaId, Instant cursor, int size) {
		List<Report> reports = reportRepository
				.findByAdministrativeArea_IdAndPublicVisibilityAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						areaId,
						ReportPublicVisibility.PUBLISHED,
						cursorOrMax(cursor),
						PageRequest.of(0, checkedPageSize(size) + 1)
				);
		return cursorResponse(reports, size);
	}

	/** 미공개 제보는 404 로 감춘다. 403 이면 "존재하지만 내려갔다"를 알려주는 셈이 된다. */
	public PublicReportView report(Long reportId) {
		return toView(requirePublished(reportId), clock.instant().plus(IMAGE_URL_EXPIRY));
	}

	public ReportAnalysisResponse analysis(Long reportId) {
		requirePublished(reportId);
		return ReportAnalysisResponse.pending(reportId);
	}

	private Report requirePublished(Long reportId) {
		return reportRepository.findByIdAndPublicVisibility(reportId, ReportPublicVisibility.PUBLISHED)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	private PublicReportCursorResponse cursorResponse(List<Report> reports, int size) {
		boolean hasNext = reports.size() > size;
		List<Report> page = hasNext ? reports.subList(0, size) : reports;
		Instant expiresAt = clock.instant().plus(IMAGE_URL_EXPIRY);
		List<PublicReportView> content = page.stream().map(report -> toView(report, expiresAt)).toList();
		Instant nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getReportedAt() : null;
		return new PublicReportCursorResponse(content, nextCursor, hasNext);
	}

	private PublicReportView toView(Report report, Instant expiresAt) {
		MediaAsset asset = report.getRepresentativeBlurAsset();
		String imageUrl = asset != null && asset.getStorageStatus() == MediaStorageStatus.AVAILABLE
				? mediaObjectStorage.createPresignedGetUrl(asset.getBucketName(), asset.getObjectKey(), IMAGE_URL_EXPIRY)
				: null;
		return PublicReportView.of(report, imageUrl, imageUrl == null ? null : expiresAt);
	}

	private Instant cursorOrMax(Instant cursor) {
		return cursor == null ? MAX_CURSOR : cursor;
	}

	private int checkedPageSize(int size) {
		if (size < 1 || size > 100) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "size는 1 이상 100 이하여야 합니다.");
		}
		return size;
	}
}
