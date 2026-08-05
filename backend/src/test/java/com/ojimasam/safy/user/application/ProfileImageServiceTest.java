package com.ojimasam.safy.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.media.application.MediaObjectMetadata;
import com.ojimasam.safy.media.application.MediaObjectStorage;

class ProfileImageServiceTest {
	private static final String BUCKET = "safy";
	private static final String STAGING_KEY = "profile/15/staging";
	private static final String CURRENT_KEY = "profile/15/current";
	private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
	// JPEG 매직바이트. ImageContentTypeDetector 는 앞 12바이트만 본다.
	private static final byte[] JPEG_HEADER = {
		(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	};

	private ProfileImageService service(MediaObjectStorage storage) {
		return new ProfileImageService(storage, BUCKET, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void issueUploadReturnsPresignedPutUrlForStagingKey() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		when(storage.createPresignedPutUrl(eq(BUCKET), eq(STAGING_KEY), any(Duration.class)))
				.thenReturn("https://minio.example.com/staging");

		IssuedProfileImageUpload issued = service(storage).issueUpload(15L, "image/JPEG");

		assertThat(issued.uploadUrl()).isEqualTo("https://minio.example.com/staging");
		// 요청 헤더로 그대로 내려가므로 소문자로 정규화해야 서명·헤더가 어긋나지 않는다.
		assertThat(issued.contentType()).isEqualTo("image/jpeg");
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
	}

	@Test
	void issueUploadRejectsUnsupportedContentType() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);

		assertThatThrownBy(() -> service(storage).issueUpload(15L, "image/heic"))
				.isInstanceOf(BusinessException.class);
	}

	// 검증을 통과한 객체만 조회 경로가 보는 current 키로 옮긴다. staging 은 반드시 정리한다.
	@Test
	void completeUploadPromotesStagingToCurrentAndClearsStaging() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		byte[] content = JPEG_HEADER.clone();
		when(storage.stat(BUCKET, STAGING_KEY)).thenReturn(new MediaObjectMetadata(2048L, "image/jpeg"));
		when(storage.readPrefix(eq(BUCKET), eq(STAGING_KEY), anyInt())).thenReturn(JPEG_HEADER);
		when(storage.read(BUCKET, STAGING_KEY)).thenReturn(content);

		service(storage).completeUpload(15L);

		verify(storage).put(BUCKET, CURRENT_KEY, content, "image/jpeg");
		verify(storage).delete(BUCKET, STAGING_KEY);
	}

	@Test
	void completeUploadRejectsOversizedImageWithoutPromoting() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		when(storage.stat(BUCKET, STAGING_KEY))
				.thenReturn(new MediaObjectMetadata(ProfileImageService.MAX_IMAGE_SIZE + 1, "image/jpeg"));

		assertThatThrownBy(() -> service(storage).completeUpload(15L)).isInstanceOf(BusinessException.class);

		verify(storage, never()).put(eq(BUCKET), eq(CURRENT_KEY), any(), any());
		// 실패해도 남겨 두면 다음 업로드 검증이 이 객체를 다시 읽는다.
		verify(storage).delete(BUCKET, STAGING_KEY);
	}

	@Test
	void completeUploadRejectsFileWhoseBytesAreNotAnImage() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		when(storage.stat(BUCKET, STAGING_KEY)).thenReturn(new MediaObjectMetadata(1024L, "image/jpeg"));
		when(storage.readPrefix(eq(BUCKET), eq(STAGING_KEY), anyInt()))
				.thenReturn("not an image".getBytes());

		assertThatThrownBy(() -> service(storage).completeUpload(15L)).isInstanceOf(BusinessException.class);

		verify(storage, never()).put(eq(BUCKET), eq(CURRENT_KEY), any(), any());
	}

	@Test
	void findUploadedImageUrlReturnsPresignedGetUrlWhenObjectExists() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		when(storage.stat(BUCKET, CURRENT_KEY)).thenReturn(new MediaObjectMetadata(2048L, "image/jpeg"));
		when(storage.createPresignedGetUrl(eq(BUCKET), eq(CURRENT_KEY), any(Duration.class)))
				.thenReturn("https://minio.example.com/current");

		assertThat(service(storage).findUploadedImageUrl(15L)).isEqualTo("https://minio.example.com/current");
	}

	// 사진을 올린 적 없는 회원이 대부분이고, MinIO 장애도 여기로 떨어진다. 어느 쪽이든
	// null 을 줘서 소셜 사진으로 표시되어야 로그인·마이페이지가 막히지 않는다.
	@Test
	void findUploadedImageUrlReturnsNullWhenObjectIsMissing() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);
		when(storage.stat(BUCKET, CURRENT_KEY)).thenThrow(new IllegalStateException("not found"));

		assertThat(service(storage).findUploadedImageUrl(15L)).isNull();
	}

	@Test
	void deleteUploadedImageRemovesBothKeys() {
		MediaObjectStorage storage = mock(MediaObjectStorage.class);

		service(storage).deleteUploadedImage(15L);

		verify(storage).delete(BUCKET, CURRENT_KEY);
		verify(storage).delete(BUCKET, STAGING_KEY);
	}
}
