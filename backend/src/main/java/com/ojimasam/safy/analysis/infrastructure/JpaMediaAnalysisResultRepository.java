package com.ojimasam.safy.analysis.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import com.ojimasam.safy.analysis.domain.MediaAnalysisResult;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResultRepository;

public interface JpaMediaAnalysisResultRepository extends JpaRepository<MediaAnalysisResult, Long>, MediaAnalysisResultRepository {
	@Override
	@Query("""
			select result
			from MediaAnalysisResult result, MediaAsset asset
			where result.sourceMediaAssetId = asset.id
			  and asset.reportId = :reportId
			order by result.observedAt asc, result.id asc
			""")
	List<MediaAnalysisResult> findAllByReportId(@Param("reportId") Long reportId);
}
