package com.ojimasam.safy.media.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.MediaProcessingJobRepository;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;
import com.ojimasam.safy.media.application.dto.CompleteImageUploadCommand;

@Service
public class CompleteImageUploadService {
	private static final int MAX_IMAGE_COUNT = 5;

	private final ReportRepository reportRepository;
	private final ReportImageRepository reportImageRepository;
	private final MediaAssetRepository mediaAssetRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final MediaProcessingJobRepository processingJobRepository;
	private final Clock clock;

	@Autowired
	public CompleteImageUploadService(ReportRepository reportRepository, ReportImageRepository reportImageRepository,
			MediaAssetRepository mediaAssetRepository, MediaObjectStorage mediaObjectStorage,
			ReportRealtimeNotifier realtimeNotifier, MediaProcessingJobRepository processingJobRepository) {
		this(reportRepository, reportImageRepository, mediaAssetRepository, mediaObjectStorage,
				realtimeNotifier, processingJobRepository, Clock.systemUTC());
	}

	CompleteImageUploadService(ReportRepository reportRepository, ReportImageRepository reportImageRepository,
			MediaAssetRepository mediaAssetRepository, MediaObjectStorage mediaObjectStorage,
			ReportRealtimeNotifier realtimeNotifier, MediaProcessingJobRepository processingJobRepository, Clock clock) {
		this.reportRepository = reportRepository;
		this.reportImageRepository = reportImageRepository;
		this.mediaAssetRepository = mediaAssetRepository;
		this.mediaObjectStorage = mediaObjectStorage;
		this.realtimeNotifier = realtimeNotifier;
		this.processingJobRepository = processingJobRepository;
		this.clock = clock;
	}

	@Transactional
	public void complete(CompleteImageUploadCommand command) {
		Report report = reportRepository.findByIdForUpdate(command.reportId())
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		try {
			report.requireMediaCreation(command.userId(), ReportType.IMAGE);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.ACCESS_DENIED, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}

		long imageCount = reportImageRepository.countByReportId(report.getId());
		if (imageCount >= MAX_IMAGE_COUNT) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "이미지는 제보당 최대 5장까지 첨부할 수 있습니다.");
		}

		MediaAsset asset = mediaAssetRepository.findByIdForUpdate(command.mediaAssetId())
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		try {
			asset.verifyAttachable(command.userId(), report.getId(), clock.instant());
			MediaObjectMetadata actual = mediaObjectStorage.stat(asset.getBucketName(), asset.getObjectKey());
			if (actual.size() <= 0 || actual.size() > IssueImageUploadService.MAX_IMAGE_SIZE) {
				throw new IllegalArgumentException("이미지 크기는 10MB 이하여야 합니다.");
			}
			String detectedContentType = ImageContentTypeDetector.detect(
					mediaObjectStorage.readPrefix(asset.getBucketName(), asset.getObjectKey(), 12)
			);
			if (!detectedContentType.equals(asset.getContentType())) {
				throw new IllegalArgumentException("요청한 이미지 형식과 실제 파일 형식이 일치하지 않습니다.");
			}
			asset.markAvailable(actual.size());
			reportImageRepository.save(ReportImage.create(report, asset, Math.toIntExact(imageCount)));
			processingJobRepository.save(MediaProcessingJob.create(
					asset.getId(), MediaProcessingJobType.ANALYZE_AND_BLUR, clock.instant()));
			realtimeNotifier.imageReady(report.getId(), asset.getId());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, exception.getMessage());
		}
	}
}
