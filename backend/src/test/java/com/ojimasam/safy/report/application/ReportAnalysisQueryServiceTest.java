package com.ojimasam.safy.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResult;
import com.ojimasam.safy.analysis.domain.MediaAnalysisResultRepository;
import com.ojimasam.safy.analysis.domain.RiskAssessment;
import com.ojimasam.safy.analysis.domain.RiskAssessmentRepository;
import com.ojimasam.safy.analysis.domain.type.RiskAssessmentStatus;
import com.ojimasam.safy.analysis.domain.risk.RiskLevel;

@ExtendWith(MockitoExtension.class)
class ReportAnalysisQueryServiceTest {
	@Mock RiskAssessmentRepository riskAssessmentRepository;
	@Mock MediaAnalysisResultRepository analysisResultRepository;

	@Test
	void returnsPendingWhenRiskAssessmentDoesNotExist() {
		when(riskAssessmentRepository.findByReportId(42L)).thenReturn(Optional.empty());

		var result = service().get(42L);

		assertThat(result.status()).isEqualTo("NOT_STARTED");
		assertThat(result.riskScore()).isNull();
		assertThat(result.crowdCount()).isNull();
	}

	@Test
	void returnsRiskAndMaximumPersonCountFromCompletedFramesOnly() {
		Instant assessedAt = Instant.parse("2026-08-02T05:00:00Z");
		RiskAssessment assessment = org.mockito.Mockito.mock(RiskAssessment.class);
		when(assessment.getAssessmentStatus()).thenReturn(RiskAssessmentStatus.COMPLETED);
		when(assessment.getRiskLevel()).thenReturn(RiskLevel.HIGH);
		when(assessment.getFinalRiskScore()).thenReturn(76.4);
		when(assessment.getRiskConfidence()).thenReturn(0.8);
		when(assessment.isConfirmRequired()).thenReturn(true);
		when(assessment.getPeakVisualCongestionScore()).thenReturn(70.0);
		when(assessment.getPeakLocalCongestionScore()).thenReturn(82.0);
		when(assessment.getPersistenceScore()).thenReturn(60.0);
		when(assessment.getGrowthScore()).thenReturn(40.0);
		when(assessment.getTotalFrameCount()).thenReturn(4);
		when(assessment.getCompletedFrameCount()).thenReturn(2);
		when(assessment.getInvalidFrameCount()).thenReturn(1);
		when(assessment.getFailedFrameCount()).thenReturn(1);
		when(assessment.getRiskRuleVersion()).thenReturn("risk-v1");
		when(assessment.getAssessedAt()).thenReturn(assessedAt);
		MediaAnalysisResult completed12 = result(FrameAnalysisStatus.COMPLETED, 12);
		MediaAnalysisResult completed5 = result(FrameAnalysisStatus.COMPLETED, 5);
		MediaAnalysisResult invalid99 = result(FrameAnalysisStatus.INVALID_FRAME, 99);
		MediaAnalysisResult failed99 = result(FrameAnalysisStatus.FAILED, 99);
		when(riskAssessmentRepository.findByReportId(42L)).thenReturn(Optional.of(assessment));
		when(analysisResultRepository.findAllByReportId(42L))
				.thenReturn(List.of(completed12, completed5, invalid99, failed99));

		var response = service().get(42L);

		assertThat(response.status()).isEqualTo("COMPLETED");
		assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH.jsonValue());
		assertThat(response.riskScore()).isEqualTo(76.4);
		assertThat(response.crowdCount()).isEqualTo(12);
		assertThat(response.completedFrameCount()).isEqualTo(2);
		assertThat(response.invalidFrameCount()).isEqualTo(1);
		assertThat(response.failedFrameCount()).isEqualTo(1);
		assertThat(response.analyzedAt()).isEqualTo(assessedAt);
	}

	private ReportAnalysisQueryService service() {
		return new ReportAnalysisQueryService(riskAssessmentRepository, analysisResultRepository);
	}

	private MediaAnalysisResult result(FrameAnalysisStatus status, int personCount) {
		MediaAnalysisResult result = org.mockito.Mockito.mock(MediaAnalysisResult.class);
		when(result.getAnalysisStatus()).thenReturn(status);
		if (status == FrameAnalysisStatus.COMPLETED) {
			when(result.getPersonCount()).thenReturn(personCount);
		}
		return result;
	}
}
