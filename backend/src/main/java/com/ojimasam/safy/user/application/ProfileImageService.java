package com.ojimasam.safy.user.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.media.application.ImageContentTypeDetector;
import com.ojimasam.safy.media.application.MediaObjectMetadata;
import com.ojimasam.safy.media.application.MediaObjectStorage;

/**
 * 회원이 직접 올린 프로필 사진을 MinIO 에만 저장한다.
 *
 * <p>객체 키를 회원당 하나로 고정해서 DB 스키마를 건드리지 않는다. 운영 프로필은
 * {@code ddl-auto: validate} 라 컬럼이나 테이블을 추가하면 운영 DB 에 DDL 을 먼저 적용하지
 * 않는 한 백엔드가 기동하지 못한다. 프로필 사진 하나 때문에 그 위험을 지지 않는 선택이다.
 *
 * <p>업로드는 staging 키로 받고, 검증을 통과한 것만 current 키로 옮긴다. presigned PUT 은
 * 크기와 형식을 강제하지 못하므로, 업로드만 하고 완료를 호출하지 않은 객체가 조회 경로에
 * 그대로 노출되는 것을 이 분리로 막는다.
 */
@Service
public class ProfileImageService {
	private static final Logger log = LoggerFactory.getLogger(ProfileImageService.class);

	private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(5);
	// 제보 이미지 미리보기(ReportQueryService.PREVIEW_URL_EXPIRY)와 같은 만료를 쓴다.
	private static final Duration VIEW_EXPIRY = Duration.ofMinutes(10);
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	// 완료 처리에서 백엔드가 객체를 한 번 읽어 옮기므로 제보 이미지(10MB)보다 작게 잡는다.
	public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

	private final MediaObjectStorage storage;
	private final String bucket;
	private final Clock clock;

	@Autowired
	public ProfileImageService(MediaObjectStorage storage, @Value("${minio.upload-bucket}") String bucket) {
		this(storage, bucket, Clock.systemUTC());
	}

	ProfileImageService(MediaObjectStorage storage, String bucket, Clock clock) {
		this.storage = storage;
		this.bucket = bucket;
		this.clock = clock;
	}

	public IssuedProfileImageUpload issueUpload(Long userId, String contentType) {
		String normalized = normalize(contentType);
		try {
			String uploadUrl = storage.createPresignedPutUrl(bucket, stagingKey(userId), UPLOAD_EXPIRY);
			return new IssuedProfileImageUpload(uploadUrl, normalized, MAX_IMAGE_SIZE,
					clock.instant().plus(UPLOAD_EXPIRY));
		} catch (IllegalStateException exception) {
			log.warn("Failed to issue profile image upload url for user {}", userId, exception);
			throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	public void completeUpload(Long userId) {
		String staging = stagingKey(userId);
		try {
			MediaObjectMetadata metadata = storage.stat(bucket, staging);
			if (metadata.size() <= 0 || metadata.size() > MAX_IMAGE_SIZE) {
				throw new IllegalArgumentException("프로필 사진은 5MB 이하만 등록할 수 있습니다.");
			}
			// 확장자나 요청 헤더가 아니라 실제 바이트로 형식을 판단한다.
			String detected = ImageContentTypeDetector.detect(storage.readPrefix(bucket, staging, 12));
			storage.put(bucket, currentKey(userId), storage.read(bucket, staging), detected);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			log.warn("Failed to complete profile image upload for user {}", userId, exception);
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
					"업로드된 프로필 사진을 확인하지 못했습니다. 다시 시도해 주세요.");
		} finally {
			deleteQuietly(staging);
		}
	}

	/** 회원이 올린 사진을 지운다. 되돌리기와 탈퇴 정리에서 함께 쓴다. 없어도 실패하지 않는다. */
	public void deleteUploadedImage(Long userId) {
		deleteQuietly(currentKey(userId));
		deleteQuietly(stagingKey(userId));
	}

	/** 올린 사진이 있으면 조회용 presigned URL, 없으면 null. 소셜 사진으로 떨어질지 결정한다. */
	public String findUploadedImageUrl(Long userId) {
		String key = currentKey(userId);
		try {
			storage.stat(bucket, key);
			return storage.createPresignedGetUrl(bucket, key, VIEW_EXPIRY);
		} catch (RuntimeException exception) {
			// 대부분은 올린 사진이 없는 정상 경우다. MinIO 장애일 때도 여기로 떨어져야
			// 소셜 사진으로 표시되고 로그인·마이페이지가 막히지 않는다.
			log.debug("No uploaded profile image for user {}", userId, exception);
			return null;
		}
	}

	private String normalize(String contentType) {
		String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
					"JPEG, PNG, WEBP 이미지만 등록할 수 있습니다.");
		}
		return normalized;
	}

	private void deleteQuietly(String objectKey) {
		try {
			storage.delete(bucket, objectKey);
		} catch (RuntimeException exception) {
			log.debug("Failed to delete profile image object {}", objectKey, exception);
		}
	}

	private String stagingKey(Long userId) {
		return "profile/%d/staging".formatted(userId);
	}

	private String currentKey(Long userId) {
		return "profile/%d/current".formatted(userId);
	}
}
