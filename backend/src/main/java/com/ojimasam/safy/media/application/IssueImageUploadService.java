package com.ojimasam.safy.media.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;

@Service
public class IssueImageUploadService {
	private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(5);
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
	private static final long MAX_ACTIVE_UPLOADS_PER_USER = 10;
	private static final long MAX_UPLOAD_ISSUES_PER_MINUTE = 20;

	private final MediaAssetRepository mediaAssetRepository;
	private final MediaObjectStorage mediaObjectStorage;
	private final String uploadBucket;
	private final Clock clock;

	@Autowired
	public IssueImageUploadService(
			MediaAssetRepository mediaAssetRepository,
			MediaObjectStorage mediaObjectStorage,
			@Value("${minio.upload-bucket}") String uploadBucket
	) {
		this(mediaAssetRepository, mediaObjectStorage, uploadBucket, Clock.systemUTC());
	}

	IssueImageUploadService(MediaAssetRepository repository, MediaObjectStorage storage, String bucket, Clock clock) {
		this.mediaAssetRepository = repository;
		this.mediaObjectStorage = storage;
		this.uploadBucket = bucket;
		this.clock = clock;
	}

	@Transactional
	public IssuedImageUpload issue(IssueImageUploadCommand command) {
		validate(command);
		Instant now = clock.instant();
		validateQuota(command.userId(), now);
		Instant expiresAt = now.plus(UPLOAD_EXPIRY);
		String objectKey = createObjectKey(command.userId(), command.filename());
		MediaAsset asset = MediaAsset.prepareOriginal(
				command.userId(), uploadBucket, objectKey, command.filename(),
				command.contentType().toLowerCase(Locale.ROOT), expiresAt
		);
		MediaAsset saved = mediaAssetRepository.save(asset);
		PresignedPostUpload upload = mediaObjectStorage.createPresignedPost(
				uploadBucket, objectKey, asset.getContentType(), expiresAt, MAX_IMAGE_SIZE
		);
		return new IssuedImageUpload(saved.getId(), upload.uploadUrl(), upload.formFields(), MAX_IMAGE_SIZE, expiresAt);
	}

	private void validate(IssueImageUploadCommand command) {
		if (command.filename() == null || command.filename().isBlank()) throw new IllegalArgumentException("파일명은 필수입니다.");
		if (command.filename().length() > 255) throw new IllegalArgumentException("파일명은 255자 이하여야 합니다.");
		if (command.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(command.contentType().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("JPEG, PNG, WEBP 이미지만 업로드할 수 있습니다.");
		}
	}

	private void validateQuota(Long userId, Instant now) {
		long activeUploads = mediaAssetRepository.countByOwnerUserIdAndStorageStatusAndUploadExpiresAtAfter(
				userId, com.ojimasam.safy.media.domain.MediaStorageStatus.PENDING_UPLOAD, now
		);
		if (activeUploads >= MAX_ACTIVE_UPLOADS_PER_USER) {
			throw new IllegalStateException("동시에 발급할 수 있는 이미지 업로드는 최대 10개입니다.");
		}
		long recentIssues = mediaAssetRepository.countByOwnerUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofMinutes(1)));
		if (recentIssues >= MAX_UPLOAD_ISSUES_PER_MINUTE) {
			throw new IllegalStateException("업로드 URL 발급 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
		}
	}

	private String createObjectKey(Long userId, String filename) {
		String extension = "";
		int dotIndex = filename.lastIndexOf('.');
		if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
			extension = "." + filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		}
		LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		return "reports/%d/%s/%s%s".formatted(userId, date, UUID.randomUUID(), extension);
	}
}
