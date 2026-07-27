package com.ojimasam.safy.media.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.MediaStorageStatus;

import jakarta.persistence.LockModeType;

public interface JpaMediaAssetRepository extends JpaRepository<MediaAsset, Long>, MediaAssetRepository {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select media from MediaAsset media where media.id in :ids")
	List<MediaAsset> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select media from MediaAsset media where media.id = :id")
	Optional<MediaAsset> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<MediaAsset> findTop100ByStorageStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAsc(
			MediaStorageStatus storageStatus, Instant now);
}
