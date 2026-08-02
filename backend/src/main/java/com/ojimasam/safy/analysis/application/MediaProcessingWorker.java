package com.ojimasam.safy.analysis.application;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisResult;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResult;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResultRepository;
import com.ojimasam.safy.analysis.domain.MediaBlurResult;
import com.ojimasam.safy.analysis.domain.MediaBlurResultRepository;
import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.MediaProcessingJobRepository;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.infrastructure.AiAnalysisClient;
import com.ojimasam.safy.analysis.infrastructure.AiBlurClient;
import com.ojimasam.safy.analysis.infrastructure.AiBlurException;
import com.ojimasam.safy.analysis.infrastructure.AiBlurFailureCode;
import com.ojimasam.safy.live.domain.ExtractedFrameRepository;
import com.ojimasam.safy.media.application.MediaObjectStorage;
import com.ojimasam.safy.media.domain.MediaAsset;
import com.ojimasam.safy.media.domain.MediaAssetRepository;
import com.ojimasam.safy.media.domain.type.MediaVariantType;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Component
public class MediaProcessingWorker {
	private static final Logger log = LoggerFactory.getLogger(MediaProcessingWorker.class);
	private static final List<MediaProcessingJobStatus> READY =
			List.of(MediaProcessingJobStatus.PENDING, MediaProcessingJobStatus.RETRY_WAIT);

	private final MediaProcessingJobRepository jobs;
	private final MediaAssetRepository assets;
	private final MediaAnalysisResultRepository results;
	private final MediaBlurResultRepository blurResults;
	private final ExtractedFrameRepository frames;
	private final ReportRepository reports;
	private final MediaObjectStorage storage;
	private final AiAnalysisClient analysisClient;
	private final AiBlurClient blurClient;
	private final RiskAssessmentService riskAssessmentService;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final TransactionTemplate transactions;
	private final Clock clock;

	@Autowired
	public MediaProcessingWorker(MediaProcessingJobRepository jobs, MediaAssetRepository assets,
			MediaAnalysisResultRepository results, MediaBlurResultRepository blurResults,
			ExtractedFrameRepository frames, ReportRepository reports, MediaObjectStorage storage,
			AiAnalysisClient analysisClient, AiBlurClient blurClient,
			RiskAssessmentService riskAssessmentService, TransactionTemplate transactions) {
		this(jobs, assets, results, blurResults, frames, reports, storage, analysisClient, blurClient,
				riskAssessmentService,
				transactions, Clock.systemUTC());
	}

	MediaProcessingWorker(MediaProcessingJobRepository jobs, MediaAssetRepository assets,
			MediaAnalysisResultRepository results, MediaBlurResultRepository blurResults,
			ExtractedFrameRepository frames, ReportRepository reports, MediaObjectStorage storage,
			AiAnalysisClient analysisClient, AiBlurClient blurClient,
			RiskAssessmentService riskAssessmentService, TransactionTemplate transactions, Clock clock) {
		this.jobs = jobs; this.assets = assets; this.results = results; this.blurResults = blurResults;
		this.frames = frames; this.reports = reports;
		this.storage = storage; this.analysisClient = analysisClient; this.blurClient = blurClient;
		this.riskAssessmentService = riskAssessmentService;
		this.transactions = transactions; this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${ai.processing.scheduler-delay:PT2S}")
	public void processPending() {
		recoverInterrupted();
		for (int i = 0; i < 10; i++) {
			Optional<Target> target = claim();
			if (target.isEmpty()) return;
			process(target.get());
		}
	}

	private void recoverInterrupted() {
		Instant now = clock.instant();
		transactions.executeWithoutResult(status -> jobs
				.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
						MediaProcessingJobStatus.PROCESSING, now.minus(Duration.ofMinutes(1)))
				.forEach(job -> job.recoverInterrupted(now)));
	}

	private Optional<Target> claim() {
		return transactions.execute(status -> jobs
				.findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(READY, clock.instant())
				.flatMap(job -> assets.findByIdForUpdate(job.getMediaAssetId()).map(asset -> {
					job.start(clock.instant());
					Instant observedAt = frames.findByMediaAssetId(asset.getId())
							.map(frame -> frame.getCapturedAt())
							.orElseGet(() -> asset.getCreatedAt() == null ? clock.instant() : asset.getCreatedAt());
					return new Target(job.getId(), asset.getId(), asset.getOwnerUserId(), asset.getReportId(),
							asset.getBucketName(), asset.getObjectKey(), asset.getContentType(), observedAt);
				})));
	}

	private void process(Target target) {
		try {
			byte[] original = storage.read(target.bucket(), target.objectKey());
			ensureAnalysisResult(target, original);
			if (completeFromExistingBlur(target)) {
				riskAssessmentService.refresh(target.reportId());
				return;
			}
			BlurredImageResult blur;
			try {
				blur = blurClient.blur(original, target.contentType());
			} catch (RuntimeException exception) {
				recordBlurFailure(target.assetId(), exception);
				throw exception;
			}
			String blurredKey = blurredObjectKey(target);
			storage.put(target.bucket(), blurredKey, blur.image(), MediaTypeNames.JPEG);
			finishBlur(target, blurredKey, blur);
			riskAssessmentService.refresh(target.reportId());
		} catch (RuntimeException exception) {
			fail(target.jobId(), exception);
			try {
				riskAssessmentService.refresh(target.reportId());
			} catch (RuntimeException assessmentException) {
				log.warn("실패 작업 반영 후 위험도 갱신에 실패했습니다. reportId={}",
						target.reportId(), assessmentException);
			}
			log.warn("미디어 AI 처리에 실패했습니다. jobId={}, assetId={}", target.jobId(), target.assetId(), exception);
		}
	}

	private void ensureAnalysisResult(Target target, byte[] original) {
		if (results.existsBySourceMediaAssetId(target.assetId())) {
			riskAssessmentService.refresh(target.reportId());
			return;
		}
		FrameAnalysisResult analysis = analysisClient.analyze(original, target.contentType());
		transactions.executeWithoutResult(status -> {
			if (!results.existsBySourceMediaAssetId(target.assetId())) {
				results.save(MediaAnalysisResult.create(target.assetId(), analysis,
						toJson(analysis.qualityIssues()), toJson(analysis.failureCodes()),
						target.observedAt(), clock.instant()));
			}
		});
		riskAssessmentService.refresh(target.reportId());
	}

	private boolean completeFromExistingBlur(Target target) {
		MediaBlurResult existing = blurResults.findBySourceMediaAssetId(target.assetId()).orElse(null);
		if (existing == null || existing.getStatus() != com.ojimasam.safy.analysis.domain.type.MediaBlurStatus.COMPLETED
				|| existing.getBlurredMediaAssetId() == null) return false;
		transactions.executeWithoutResult(status -> {
			MediaProcessingJob job = jobs.findByIdForUpdate(target.jobId()).orElseThrow();
			if (job.getStatus() != MediaProcessingJobStatus.PROCESSING) return;
			assets.findByIdForUpdate(existing.getBlurredMediaAssetId()).ifPresent(blurred ->
					reports.findByIdForUpdate(target.reportId()).ifPresent(report ->
							report.applyRepresentativeBlurAsset(blurred)));
			job.complete(clock.instant());
		});
		return true;
	}

	private String blurredObjectKey(Target target) {
		if (target.objectKey().startsWith("frames/")) {
			int separator = target.objectKey().lastIndexOf('/');
			return target.objectKey().substring(0, separator + 1) + "blurred/"
					+ target.objectKey().substring(separator + 1);
		}
		return "reports/%d/blurred/%d.jpg".formatted(target.reportId(), target.assetId());
	}

	private void finishBlur(Target target, String blurredKey, BlurredImageResult blur) {
		transactions.executeWithoutResult(status -> {
			MediaProcessingJob job = jobs.findByIdForUpdate(target.jobId()).orElseThrow();
			if (job.getStatus() != MediaProcessingJobStatus.PROCESSING) return;
			MediaAsset source = assets.findByIdForUpdate(target.assetId()).orElseThrow();
			MediaAsset blurred = assets.findByBucketNameAndObjectKey(target.bucket(), blurredKey)
					.orElseGet(() -> assets.save(MediaAsset.recordServerObject(target.ownerUserId(), target.reportId(),
							target.bucket(), blurredKey, null, MediaTypeNames.JPEG, blur.image().length,
							MediaVariantType.BLURRED, source, clock.instant())));
			MediaBlurResult blurResult = blurResults.findBySourceMediaAssetIdForUpdate(source.getId())
					.orElseGet(() -> MediaBlurResult.completed(source.getId(), blurred.getId(), blur, clock.instant()));
			if (blurResult.getId() != null) blurResult.complete(blurred.getId(), blur, clock.instant());
			blurResults.save(blurResult);
			Report report = reports.findByIdForUpdate(target.reportId()).orElseThrow();
			report.applyRepresentativeBlurAsset(blurred);
			job.complete(clock.instant());
		});
	}

	private void recordBlurFailure(Long sourceMediaAssetId, RuntimeException exception) {
		AiBlurFailureCode code = exception instanceof AiBlurException blurException
				? blurException.getFailureCode() : AiBlurFailureCode.INVALID_AI_RESPONSE;
		String failureCodes = toJson(List.of(code.name()));
		transactions.executeWithoutResult(status -> {
			MediaBlurResult result = blurResults.findBySourceMediaAssetIdForUpdate(sourceMediaAssetId)
					.orElseGet(() -> MediaBlurResult.failed(sourceMediaAssetId, failureCodes, clock.instant()));
			if (result.getId() != null) result.recordFailure(failureCodes, clock.instant());
			blurResults.save(result);
		});
	}

	private void fail(Long jobId, RuntimeException exception) {
		transactions.executeWithoutResult(status -> jobs.findByIdForUpdate(jobId).ifPresent(job -> {
			if (job.getStatus() == MediaProcessingJobStatus.PROCESSING) {
				job.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), clock.instant());
			}
		}));
	}

	private String toJson(List<String> values) {
		try { return objectMapper.writeValueAsString(values); }
		catch (JsonProcessingException exception) { throw new IllegalStateException("분석 코드 직렬화에 실패했습니다.", exception); }
	}

	private record Target(Long jobId, Long assetId, Long ownerUserId, Long reportId,
			String bucket, String objectKey, String contentType, Instant observedAt) { }

	private static final class MediaTypeNames {
		private static final String JPEG = "image/jpeg";
	}
}
