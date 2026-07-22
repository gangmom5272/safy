package com.ojimasam.safy.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

import com.ojimasam.safy.report.domain.ReportImage;
import com.ojimasam.safy.report.domain.ReportImageRepository;

public interface JpaReportImageRepository extends JpaRepository<ReportImage, Long>, ReportImageRepository {
	@Override
	@EntityGraph(attributePaths = {"mediaAsset", "report"})
	List<ReportImage> findAllByReportIdOrderByDisplayOrderAsc(Long reportId);

	@Override
	@EntityGraph(attributePaths = {"mediaAsset", "report"})
	List<ReportImage> findAllByReportIdInOrderByReportIdAscDisplayOrderAsc(Collection<Long> reportIds);
}
