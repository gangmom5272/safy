package com.ojimasam.safy.media.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaStorageStatus;

@ExtendWith(MockitoExtension.class)
class ExpiredMediaCleanupServiceTest {
	@Mock MediaAssetRepository repository;
	@Mock MediaObjectStorage storage;

	@Test
	void deletesExpiredPendingObjectAndDatabaseRecord() {
		Instant now = Instant.parse("2026-07-21T04:10:00Z");
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, 10L, "bucket", "reports/1/image.jpg", "image.jpg", "image/jpeg", now.minusSeconds(1)
		);
		when(repository.findTop100ByStorageStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
				MediaStorageStatus.PENDING_UPLOAD, now)).thenReturn(List.of(asset));
		ExpiredMediaCleanupService service = new ExpiredMediaCleanupService(
				repository, storage, Clock.fixed(now, ZoneOffset.UTC)
		);

		service.cleanup();

		verify(storage).delete("bucket", "reports/1/image.jpg");
		verify(repository).delete(asset);
	}
}
