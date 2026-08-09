package com.ojimasam.safy.analysis.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaBlurResultRepository {
	MediaBlurResult save(MediaBlurResult result);
	Optional<MediaBlurResult> findBySourceMediaAssetId(Long sourceMediaAssetId);
	List<MediaBlurResult> findAllBySourceMediaAssetIdIn(Collection<Long> sourceMediaAssetIds);
	Optional<MediaBlurResult> findBySourceMediaAssetIdForUpdate(Long sourceMediaAssetId);
}
