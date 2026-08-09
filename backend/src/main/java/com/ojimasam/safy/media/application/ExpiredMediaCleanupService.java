package com.ojimasam.safy.media.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;

@Service
public class ExpiredMediaCleanupService {
	private static final Logger log = LoggerFactory.getLogger(ExpiredMediaCleanupService.class);

	private final MediaAssetRepository mediaAssetRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final Clock clock;

	@Autowired
	public ExpiredMediaCleanupService(MediaAssetRepository mediaAssetRepository,
			MediaObjectStorage mediaObjectStorage) {
		this(mediaAssetRepository, mediaObjectStorage, Clock.systemUTC());
	}

	ExpiredMediaCleanupService(MediaAssetRepository mediaAssetRepository,
			MediaObjectStorage mediaObjectStorage, Clock clock) {
		this.mediaAssetRepository = mediaAssetRepository;
		this.mediaObjectStorage = mediaObjectStorage;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${media.upload.cleanup-delay:PT5M}")
	@Transactional
	public void cleanup() {
		for (MediaAsset asset : mediaAssetRepository
				.findTop100ByStorageStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
				MediaStorageStatus.PENDING_UPLOAD, clock.instant())) {
			try {
				mediaObjectStorage.delete(asset.getBucketName(), asset.getObjectKey());
				asset.markExpired();
				mediaAssetRepository.delete(asset);
			} catch (IllegalStateException exception) {
				log.warn("Failed to clean expired media asset {}", asset.getId(), exception);
			}
		}
	}
}
