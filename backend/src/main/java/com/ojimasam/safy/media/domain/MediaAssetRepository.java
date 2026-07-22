package com.ojimasam.safy.media.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MediaAssetRepository {
	MediaAsset save(MediaAsset mediaAsset);
	List<MediaAsset> findAllById(Iterable<Long> ids);
	List<MediaAsset> findAllByIdForUpdate(Collection<Long> ids);
	long countByOwnerUserIdAndStorageStatusAndUploadExpiresAtAfter(Long ownerUserId,
			MediaStorageStatus storageStatus, Instant now);
	long countByOwnerUserIdAndCreatedAtAfter(Long ownerUserId, Instant createdAfter);
	List<MediaAsset> findTop100ByStorageStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
			MediaStorageStatus storageStatus, Instant now);
	void delete(MediaAsset mediaAsset);
}
