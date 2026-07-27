package com.ojimasam.safy.media.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
		name = "media_assets",
		uniqueConstraints = @UniqueConstraint(name = "uk_media_assets_object", columnNames = {"bucket_name", "object_key"})
)
public class MediaAsset extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	private long version;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Column(name = "report_id", nullable = false)
	private Long reportId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_media_asset_id")
	private MediaAsset sourceMediaAsset;

	@Column(name = "bucket_name", nullable = false, length = 100)
	private String bucketName;

	@Column(name = "object_key", nullable = false, length = 500)
	private String objectKey;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "file_size")
	private Long fileSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "variant_type", nullable = false, length = 20)
	private MediaVariantType variantType;

	@Enumerated(EnumType.STRING)
	@Column(name = "storage_status", nullable = false, length = 30)
	private MediaStorageStatus storageStatus;

	@Column(name = "upload_expires_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant uploadExpiresAt;

	protected MediaAsset() {
	}

	private MediaAsset(Long ownerUserId, Long reportId, String bucketName, String objectKey,
			String originalFilename, String contentType, Instant uploadExpiresAt) {
		this.ownerUserId = ownerUserId;
		this.reportId = reportId;
		this.bucketName = bucketName;
		this.objectKey = objectKey;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.variantType = MediaVariantType.ORIGINAL;
		this.storageStatus = MediaStorageStatus.PENDING_UPLOAD;
		this.uploadExpiresAt = uploadExpiresAt;
	}

	public static MediaAsset prepareOriginal(Long ownerUserId, Long reportId, String bucketName, String objectKey,
			String originalFilename, String contentType, Instant uploadExpiresAt) {
		if (ownerUserId == null || ownerUserId <= 0) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
		if (reportId == null || reportId <= 0) throw new IllegalArgumentException("제보 ID는 필수입니다.");
		if (bucketName == null || bucketName.isBlank()) throw new IllegalArgumentException("버킷 이름은 필수입니다.");
		if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("객체 키는 필수입니다.");
		if (originalFilename == null || originalFilename.isBlank()) throw new IllegalArgumentException("원본 파일명은 필수입니다.");
		if (originalFilename.length() > 255) throw new IllegalArgumentException("원본 파일명은 255자 이하여야 합니다.");
		if (contentType == null || !contentType.startsWith("image/")) throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
		if (uploadExpiresAt == null) throw new IllegalArgumentException("업로드 만료 시각은 필수입니다.");
		return new MediaAsset(ownerUserId, reportId, bucketName, objectKey, originalFilename, contentType, uploadExpiresAt);
	}

	public void verifyAttachable(Long userId, Long targetReportId, Instant now) {
		if (!ownerUserId.equals(userId)) throw new IllegalArgumentException("다른 사용자의 미디어입니다.");
		if (!reportId.equals(targetReportId)) throw new IllegalArgumentException("다른 제보에 발급된 미디어입니다.");
		if (storageStatus != MediaStorageStatus.PENDING_UPLOAD) throw new IllegalStateException("이미 사용되었거나 첨부할 수 없는 미디어입니다.");
		if (uploadExpiresAt.isBefore(now)) throw new IllegalArgumentException("업로드가 만료되었습니다.");
	}

	public void markAvailable(long actualFileSize) {
		if (actualFileSize <= 0) throw new IllegalArgumentException("업로드된 이미지가 비어 있습니다.");
		this.fileSize = actualFileSize;
		this.storageStatus = MediaStorageStatus.AVAILABLE;
	}

	public void markExpired() {
		if (storageStatus == MediaStorageStatus.PENDING_UPLOAD) {
			storageStatus = MediaStorageStatus.EXPIRED;
		}
	}

	public Long getId() { return id; }
	public Long getOwnerUserId() { return ownerUserId; }
	public Long getReportId() { return reportId; }
	public String getBucketName() { return bucketName; }
	public String getObjectKey() { return objectKey; }
	public String getOriginalFilename() { return originalFilename; }
	public String getContentType() { return contentType; }
	public Long getFileSize() { return fileSize; }
	public MediaVariantType getVariantType() { return variantType; }
	public MediaStorageStatus getStorageStatus() { return storageStatus; }
	public Instant getUploadExpiresAt() { return uploadExpiresAt; }
}
