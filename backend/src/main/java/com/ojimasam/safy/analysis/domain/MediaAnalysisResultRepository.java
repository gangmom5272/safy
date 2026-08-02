package com.ojimasam.safy.analysis.domain;

import java.util.List;
import java.util.Optional;

public interface MediaAnalysisResultRepository {
	MediaAnalysisResult save(MediaAnalysisResult result);
	boolean existsBySourceMediaAssetId(Long sourceMediaAssetId);
	Optional<MediaAnalysisResult> findBySourceMediaAssetId(Long sourceMediaAssetId);
	List<MediaAnalysisResult> findAllByReportId(Long reportId);
}
