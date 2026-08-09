package com.ojimasam.safy.analysis.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.analysis.application.dto.CongestionSource;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResult;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResultRepository;
import com.ojimasam.safy.analysis.domain.MediaBlurResultRepository;
import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.MediaProcessingJobRepository;
import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentCounts;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.risk.FrameObservation;
import com.ojimasam.safy.analysis.domain.risk.RiskCalculation;
import com.ojimasam.safy.analysis.domain.risk.RiskAssessor;
import com.ojimasam.safy.analysis.domain.type.MediaBlurStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.application.ReportRealtimeNotifier;

@Service
public class RiskAssessmentService {
	private final MediaAnalysisResultRepository analysisResults;
	private final MediaBlurResultRepository blurResults;
	private final MediaProcessingJobRepository jobs;
	private final RiskAssessmentRepository assessments;
	private final MediaAssetRepository assets;
	private final ReportRepository reports;
	private final ReportRealtimeNotifier realtimeNotifier;
	private final Clock clock;

	@Autowired
	public RiskAssessmentService(MediaAnalysisResultRepository analysisResults,
			MediaBlurResultRepository blurResults, MediaProcessingJobRepository jobs,
			RiskAssessmentRepository assessments, MediaAssetRepository assets, ReportRepository reports,
			ReportRealtimeNotifier realtimeNotifier) {
		this(analysisResults, blurResults, jobs, assessments, assets, reports, realtimeNotifier, Clock.systemUTC());
	}

	RiskAssessmentService(MediaAnalysisResultRepository analysisResults,
			MediaBlurResultRepository blurResults, MediaProcessingJobRepository jobs,
			RiskAssessmentRepository assessments, MediaAssetRepository assets,
			ReportRepository reports, ReportRealtimeNotifier realtimeNotifier, Clock clock) {
		this.analysisResults = analysisResults;
		this.blurResults = blurResults;
		this.jobs = jobs;
		this.assessments = assessments;
		this.assets = assets;
		this.reports = reports;
		this.realtimeNotifier = realtimeNotifier;
		this.clock = clock;
	}

	@Transactional
	public RiskAssessment refresh(Long reportId) {
		Report report = reports.findByIdForUpdate(reportId).orElseThrow();
		List<MediaAnalysisResult> allResults = analysisResults.findAllByReportId(reportId);
		List<MediaProcessingJob> allJobs = jobs.findAllByReportId(reportId);
		List<MediaAnalysisResult> completed = allResults.stream()
				.filter(this::isUsableCompletedResult)
				.toList();

		Set<CongestionSource> sources = new HashSet<>();
		completed.forEach(result -> sources.add(result.getCongestionSource()));
		boolean sourceMixed = sources.size() > 1;
		int qualityClean = (int) completed.stream().filter(MediaAnalysisResult::hasNoQualityIssues).count();
		int invalid = (int) allResults.stream()
				.filter(result -> result.getAnalysisStatus() == FrameAnalysisStatus.INVALID_FRAME).count();
		int failedResponse = (int) allResults.stream()
				.filter(result -> result.getAnalysisStatus() == FrameAnalysisStatus.FAILED).count();
		Set<Long> resultSourceIds = new HashSet<>(allResults.stream()
				.map(MediaAnalysisResult::getSourceMediaAssetId).toList());
		int failedWithoutResult = (int) allJobs.stream()
				.filter(job -> job.getStatus() == MediaProcessingJobStatus.FAILED)
				.filter(job -> !resultSourceIds.contains(job.getMediaAssetId()))
				.count();

		List<FrameObservation> observations = completed.stream()
				.map(result -> new FrameObservation(result.getSourceMediaAssetId(), result.getObservedAt(),
						result.getVisualCongestionScore(), result.getLocalPeakCongestionScore()))
				.toList();
		RiskCalculation calculation = RiskAssessor.assess(
				observations, allJobs.size(), qualityClean, sourceMixed);
		Long representativeBlurAssetId = representativeBlurAssetId(calculation);
		RiskAssessmentCounts counts = new RiskAssessmentCounts(allJobs.size(), completed.size(), invalid,
				failedResponse + failedWithoutResult, qualityClean, sourceMixed);
		RiskAssessment assessment = assessments.findByReportIdForUpdate(reportId)
				.orElseGet(() -> RiskAssessment.create(reportId, calculation, counts,
						representativeBlurAssetId, clock.instant()));
		boolean newlyCreated = assessment.getId() == null;
		var previousStatus = assessment.getAssessmentStatus();
		var previousLevel = assessment.getRiskLevel();
		Double previousScore = assessment.getFinalRiskScore();
		boolean previousConfirmRequired = assessment.isConfirmRequired();
		if (assessment.getId() != null) {
			assessment.refresh(calculation, counts, representativeBlurAssetId, clock.instant());
		}
		RiskAssessment saved = assessments.save(assessment);

		if (representativeBlurAssetId != null) {
			assets.findByIdForUpdate(representativeBlurAssetId)
					.ifPresent(report::applyRepresentativeBlurAsset);
		}
		if (realtimeNotifier != null && (newlyCreated
				|| previousStatus != saved.getAssessmentStatus()
				|| previousLevel != saved.getRiskLevel()
				|| !Objects.equals(previousScore, saved.getFinalRiskScore())
				|| previousConfirmRequired != saved.isConfirmRequired())) {
			realtimeNotifier.riskAssessmentUpdated(reportId);
		}
		return saved;
	}

	private boolean isUsableCompletedResult(MediaAnalysisResult result) {
		return result.getAnalysisStatus() == FrameAnalysisStatus.COMPLETED
				&& result.getVisualCongestionScore() != null
				&& result.getLocalPeakCongestionScore() != null;
	}

	private Long representativeBlurAssetId(RiskCalculation calculation) {
		Long sourceId = calculation.representativeSourceMediaAssetId();
		if (sourceId == null) return null;
		return blurResults.findBySourceMediaAssetId(sourceId)
				.filter(result -> result.getStatus() == MediaBlurStatus.COMPLETED)
				.map(result -> result.getBlurredMediaAssetId())
				.orElse(null);
	}
}
