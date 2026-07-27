package com.ojimasam.safy.media.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository {
	MediaAsset save(MediaAsset mediaAsset);
	List<MediaAsset> findAllById(Iterable<Long> ids);
	List<MediaAsset> findAllByIdForUpdate(Collection<Long> ids);
	Optional<MediaAsset> findByIdForUpdate(Long id);
	long countByReportIdAndStorageStatusIn(Long reportId, Collection<MediaStorageStatus> storageStatuses);
	long countByOwnerUserIdAndStorageStatusAndUploadExpiresAtAfter(Long ownerUserId,
			MediaStorageStatus storageStatus, Instant now);
	long countByOwnerUserIdAndCreatedAtAfter(Long ownerUserId, Instant createdAfter);
	List<MediaAsset> findTop100ByStorageStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
			MediaStorageStatus storageStatus, Instant now);
	void delete(MediaAsset mediaAsset);
}
