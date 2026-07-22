package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.media.application.MediaObjectMetadata;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.application.ImageContentTypeDetector;
import com.ojimasam.safy.media.application.IssueImageUploadService;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class CreateImageReportService {
	private static final int MAX_IMAGE_COUNT = 5;

	private final ReportRepository reportRepository;
	private final ReportImageRepository reportImageRepository;
	private final MediaAssetRepository mediaAssetRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final Clock clock;

	@Autowired
	public CreateImageReportService(ReportRepository reportRepository, ReportImageRepository reportImageRepository,
			MediaAssetRepository mediaAssetRepository, MediaObjectStorage mediaObjectStorage) {
		this(reportRepository, reportImageRepository, mediaAssetRepository, mediaObjectStorage, Clock.systemUTC());
	}

	CreateImageReportService(ReportRepository reports, ReportImageRepository images,
			MediaAssetRepository assets, MediaObjectStorage storage, Clock clock) {
		this.reportRepository = reports;
		this.reportImageRepository = images;
		this.mediaAssetRepository = assets;
		this.mediaObjectStorage = storage;
		this.clock = clock;
	}

	@Transactional
	public CreateReportResult create(CreateImageReportCommand command) {
		validateIds(command.mediaAssetIds());
		List<MediaAsset> foundAssets = mediaAssetRepository.findAllByIdForUpdate(command.mediaAssetIds());
		if (foundAssets.size() != command.mediaAssetIds().size()) throw new IllegalArgumentException("존재하지 않는 미디어가 포함되어 있습니다.");

		Map<Long, MediaAsset> assetsById = new HashMap<>();
		for (MediaAsset asset : foundAssets) assetsById.put(asset.getId(), asset);
		List<MediaAsset> orderedAssets = command.mediaAssetIds().stream().map(assetsById::get).toList();

		for (MediaAsset asset : orderedAssets) {
			asset.verifyAttachable(command.reporterId(), clock.instant());
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
		}

		Report report = reportRepository.save(Report.createImage(
				command.reporterId(), command.description(), command.latitude(), command.longitude(), command.address()
		));
		for (int index = 0; index < orderedAssets.size(); index++) {
			reportImageRepository.save(ReportImage.create(report, orderedAssets.get(index), index));
		}
		return CreateReportResult.from(report);
	}

	private void validateIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("이미지를 한 장 이상 첨부해야 합니다.");
		if (ids.size() > MAX_IMAGE_COUNT) throw new IllegalArgumentException("이미지는 최대 5장까지 첨부할 수 있습니다.");
		if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("미디어 ID가 올바르지 않습니다.");
		if (ids.stream().distinct().count() != ids.size()) throw new IllegalArgumentException("중복된 미디어 ID가 있습니다.");
	}
}
