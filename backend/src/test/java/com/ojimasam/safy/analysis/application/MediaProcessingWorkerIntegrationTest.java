package com.ojimasam.safy.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;
import com.ojimasam.safy.analysis.application.dto.CongestionSource;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisResult;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;
import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.type.MediaBlurStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;
import com.ojimasam.safy.analysis.infrastructure.AiAnalysisClient;
import com.ojimasam.safy.analysis.infrastructure.AiAnalysisException;
import com.ojimasam.safy.analysis.infrastructure.AiAnalysisFailureCode;
import com.ojimasam.safy.analysis.infrastructure.AiBlurClient;
import com.ojimasam.safy.analysis.infrastructure.AiBlurException;
import com.ojimasam.safy.analysis.infrastructure.AiBlurFailureCode;
import com.ojimasam.safy.analysis.infrastructure.JpaMediaBlurResultRepository;
import com.ojimasam.safy.analysis.infrastructure.JpaMediaAnalysisResultRepository;
import com.ojimasam.safy.analysis.infrastructure.JpaMediaProcessingJobRepository;
import com.ojimasam.safy.analysis.infrastructure.JpaRiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;
import com.ojimasam.safy.analysis.domain.type.RiskAssessmentStatus;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.media.infrastructure.JpaMediaAssetRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.infrastructure.JpaReportRepository;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MediaProcessingWorkerIntegrationTest {
	@Autowired MediaProcessingWorker worker;
	@Autowired JpaReportRepository reports;
	@Autowired JpaMediaAssetRepository assets;
	@Autowired JpaMediaProcessingJobRepository jobs;
	@Autowired JpaMediaAnalysisResultRepository results;
	@Autowired JpaMediaBlurResultRepository blurResults;
	@Autowired JpaRiskAssessmentRepository riskAssessments;
	@MockitoBean MediaObjectStorage storage;
	@MockitoBean AiAnalysisClient analysisClient;
	@MockitoBean AiBlurClient blurClient;

	@Test
	void analyzesBlursStoresAndCompletesOneImageJob() {
		Report report = reports.saveAndFlush(Report.create(1L, ReportType.IMAGE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "현장 이미지",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null));
		MediaAsset source = assets.saveAndFlush(MediaAsset.recordServerObject(1L, report.getId(), "safy",
				"reports/%d/original.jpg".formatted(report.getId()), "original.jpg", "image/jpeg",
				100L, MediaVariantType.ORIGINAL, null, Instant.now()));
		MediaProcessingJob job = jobs.saveAndFlush(MediaProcessingJob.create(
				source.getId(), MediaProcessingJobType.ANALYZE_AND_BLUR, Instant.now()));
		byte[] original = "original-image".getBytes();
		byte[] blurredBytes = "blurred-image".getBytes();
		when(storage.read("safy", source.getObjectKey())).thenReturn(original);
		when(analysisClient.analyze(original, "image/jpeg")).thenReturn(completedAnalysis());
		when(blurClient.blur(original, "image/jpeg"))
				.thenReturn(new BlurredImageResult(blurredBytes, "full-image-blur-v0.1", 1280, 720, 24));

		worker.processPending();

		MediaProcessingJob completed = jobs.findById(job.getId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(MediaProcessingJobStatus.COMPLETED);
		assertThat(results.existsBySourceMediaAssetId(source.getId())).isTrue();
		assertThat(results.findBySourceMediaAssetId(source.getId()).orElseThrow().getFrameWidth()).isEqualTo(1280);
		MediaAsset blurred = assets.findAll().stream()
				.filter(asset -> asset.getVariantType() == MediaVariantType.BLURRED)
				.findFirst().orElseThrow();
		assertThat(blurred.getSourceMediaAsset().getId()).isEqualTo(source.getId());
		assertThat(blurResults.findBySourceMediaAssetId(source.getId()).orElseThrow().getStatus())
				.isEqualTo(MediaBlurStatus.COMPLETED);
		assertThat(blurResults.findBySourceMediaAssetId(source.getId()).orElseThrow().getBlurredMediaAssetId())
				.isEqualTo(blurred.getId());
		assertThat(riskAssessments.findByReportId(report.getId()).orElseThrow().getRiskLevel())
				.isEqualTo(RiskLevel.VERY_LOW);
		assertThat(riskAssessments.findByReportId(report.getId()).orElseThrow().getRepresentativeBlurAssetId())
				.isEqualTo(blurred.getId());
		assertThat(reports.findById(report.getId()).orElseThrow().getRepresentativeBlurAsset().getId())
				.isEqualTo(blurred.getId());
		verify(storage).put(eq("safy"), eq("reports/%d/blurred/%d.jpg".formatted(report.getId(), source.getId())),
				eq(blurredBytes), eq("image/jpeg"));
	}

	@Test
	void keepsAnalysisAndRecordsBlurFailureWhenBlurApiFails() {
		Report report = reports.saveAndFlush(Report.create(2L, ReportType.IMAGE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "블러 실패 이미지",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null));
		MediaAsset source = assets.saveAndFlush(MediaAsset.recordServerObject(2L, report.getId(), "safy",
				"reports/%d/original.jpg".formatted(report.getId()), "original.jpg", "image/jpeg",
				100L, MediaVariantType.ORIGINAL, null, Instant.now()));
		MediaProcessingJob job = jobs.saveAndFlush(MediaProcessingJob.create(
				source.getId(), MediaProcessingJobType.ANALYZE_AND_BLUR, Instant.now()));
		byte[] original = "original-image".getBytes();
		when(storage.read("safy", source.getObjectKey())).thenReturn(original);
		when(analysisClient.analyze(original, "image/jpeg")).thenReturn(completedAnalysis());
		when(blurClient.blur(original, "image/jpeg")).thenThrow(new AiBlurException(
				AiBlurFailureCode.IMAGE_DECODE_FAILED, "decode failed"));

		worker.processPending();

		assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
				.isEqualTo(MediaProcessingJobStatus.FAILED);
		assertThat(results.existsBySourceMediaAssetId(source.getId())).isTrue();
		assertThat(blurResults.findBySourceMediaAssetId(source.getId()).orElseThrow().getStatus())
				.isEqualTo(MediaBlurStatus.FAILED);
		assertThat(blurResults.findBySourceMediaAssetId(source.getId()).orElseThrow().getFailureCodes())
				.contains("IMAGE_DECODE_FAILED");
		assertThat(riskAssessments.findByReportId(report.getId())).isPresent();
	}

	@Test
	void recordsNotAssessableRiskWhenAnalysisServerCannotBeReached() {
		Report report = reports.saveAndFlush(Report.create(3L, ReportType.IMAGE,
				ReportCategory.ACCIDENT_RISK, ReportSubcategory.FIRE_SMOKE, "분석 통신 실패 이미지",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null));
		MediaAsset source = assets.saveAndFlush(MediaAsset.recordServerObject(3L, report.getId(), "safy",
				"reports/%d/original.jpg".formatted(report.getId()), "original.jpg", "image/jpeg",
				100L, MediaVariantType.ORIGINAL, null, Instant.now()));
		MediaProcessingJob job = jobs.saveAndFlush(MediaProcessingJob.create(
				source.getId(), MediaProcessingJobType.ANALYZE_AND_BLUR, Instant.now()));
		byte[] original = "original-image".getBytes();
		when(storage.read("safy", source.getObjectKey())).thenReturn(original);
		when(analysisClient.analyze(original, "image/jpeg")).thenThrow(new AiAnalysisException(
				AiAnalysisFailureCode.AI_CONNECTION_FAILED, "connection failed"));

		worker.processPending();

		assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
				.isEqualTo(MediaProcessingJobStatus.FAILED);
		assertThat(results.findBySourceMediaAssetId(source.getId())).isEmpty();
		assertThat(blurResults.findBySourceMediaAssetId(source.getId())).isEmpty();
		assertThat(riskAssessments.findByReportId(report.getId()).orElseThrow().getAssessmentStatus())
				.isEqualTo(RiskAssessmentStatus.NOT_ASSESSABLE);
		assertThat(riskAssessments.findByReportId(report.getId()).orElseThrow().getFailedFrameCount())
				.isEqualTo(1);
	}

	private FrameAnalysisResult completedAnalysis() {
		return new FrameAnalysisResult(FrameAnalysisStatus.COMPLETED, 3, 5.62, 30.0,
				CongestionSource.YOLO, List.of(), List.of(), 1280, 720, 86.33, 2364.53,
				"YOLO", "yolo11n", "yolo11n.pt", "yolo-grid-v0.2", "frame-quality-v0.3", 89);
	}
}
