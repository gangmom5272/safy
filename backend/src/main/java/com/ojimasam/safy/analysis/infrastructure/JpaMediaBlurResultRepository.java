package com.ojimasam.safy.analysis.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.analysis.domain.MediaBlurResult;
import com.ojimasam.safy.analysis.domain.MediaBlurResultRepository;

import jakarta.persistence.LockModeType;

public interface JpaMediaBlurResultRepository
		extends JpaRepository<MediaBlurResult, Long>, MediaBlurResultRepository {
	@Override
	List<MediaBlurResult> findAllBySourceMediaAssetIdIn(Collection<Long> sourceMediaAssetIds);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select result from MediaBlurResult result where result.sourceMediaAssetId = :sourceId")
	Optional<MediaBlurResult> findBySourceMediaAssetIdForUpdate(@Param("sourceId") Long sourceMediaAssetId);
}
