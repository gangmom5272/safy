package com.ojimasam.safy.analysis.infrastructure;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import com.ojimasam.safy.analysis.application.dto.FrameAnalysisResult;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;

@Component
public class AiAnalysisClient {
	private static final String ANALYZE_PATH = "/api/v1/analyze/frame";
	private static final int MAX_ATTEMPTS = 2;

	private final RestClient restClient;

	public AiAnalysisClient(@Qualifier("aiAnalysisRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	public FrameAnalysisResult analyze(byte[] image, String contentType) {
		if (image == null) throw new IllegalArgumentException("분석할 이미지가 필요합니다.");
		MediaType imageContentType = parseContentType(contentType);

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				FrameAnalysisResult result = request(image, imageContentType);
				validate(result);
				if (result.analysisStatus() != FrameAnalysisStatus.FAILED || attempt == MAX_ATTEMPTS) {
					return result;
				}
			} catch (HttpClientErrorException exception) {
				throw rejectedRequest(exception);
			} catch (ResourceAccessException exception) {
				if (attempt == MAX_ATTEMPTS) throw communicationFailure(exception);
			} catch (RestClientException exception) {
				throw new AiAnalysisException(
						AiAnalysisFailureCode.INVALID_AI_RESPONSE,
						"AI 분석 응답을 처리할 수 없습니다.", exception);
			}
		}
		throw new IllegalStateException("AI 분석 재시도 흐름이 종료되지 않았습니다.");
	}

	private FrameAnalysisResult request(byte[] image, MediaType contentType) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("image", new NamedByteArrayResource(image)).contentType(contentType);
		return restClient.post()
				.uri(ANALYZE_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body.build())
				.retrieve()
				.body(FrameAnalysisResult.class);
	}

	private MediaType parseContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			throw new IllegalArgumentException("이미지 Content-Type은 필수입니다.");
		}
		try {
			return MediaType.parseMediaType(contentType);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("올바르지 않은 이미지 Content-Type입니다.", exception);
		}
	}

	private void validate(FrameAnalysisResult result) {
		if (result == null || result.analysisStatus() == null || result.congestionSource() == null
				|| isBlank(result.modelName()) || isBlank(result.modelVersion())
				|| isBlank(result.modelWeightId()) || isBlank(result.congestionRuleVersion())
				|| isBlank(result.frameQualityRuleVersion()) || result.processingTimeMs() == null) {
			throw new AiAnalysisException(
					AiAnalysisFailureCode.INVALID_AI_RESPONSE, "AI 분석 응답의 필수 필드가 누락되었습니다.");
		}
		if (result.analysisStatus() == FrameAnalysisStatus.COMPLETED
				&& (result.personCount() == null || result.visualCongestionScore() == null
				|| result.localPeakCongestionScore() == null || !result.failureCodes().isEmpty())) {
			throw invalidStatusResponse();
		}
		if (result.analysisStatus() == FrameAnalysisStatus.INVALID_FRAME
				&& (result.qualityIssues().isEmpty() || !result.failureCodes().isEmpty())) {
			throw invalidStatusResponse();
		}
		if (result.analysisStatus() == FrameAnalysisStatus.FAILED && result.failureCodes().isEmpty()) {
			throw invalidStatusResponse();
		}
	}

	private AiAnalysisException invalidStatusResponse() {
		return new AiAnalysisException(
				AiAnalysisFailureCode.INVALID_AI_RESPONSE, "AI 분석 상태와 응답 필드가 일치하지 않습니다.");
	}

	private AiAnalysisException rejectedRequest(HttpClientErrorException exception) {
		AiAnalysisFailureCode code = exception.getStatusCode() == HttpStatus.UNSUPPORTED_MEDIA_TYPE
				? AiAnalysisFailureCode.UNSUPPORTED_MEDIA_TYPE
				: exception.getStatusCode() == HttpStatus.BAD_REQUEST
						? AiAnalysisFailureCode.EMPTY_IMAGE
						: AiAnalysisFailureCode.INVALID_AI_RESPONSE;
		return new AiAnalysisException(code, "AI 분석 요청이 거부되었습니다: HTTP "
				+ exception.getStatusCode().value(), exception);
	}

	private AiAnalysisException communicationFailure(ResourceAccessException exception) {
		AiAnalysisFailureCode code = hasCause(exception, SocketTimeoutException.class)
				|| hasCause(exception, HttpTimeoutException.class)
				? AiAnalysisFailureCode.AI_TIMEOUT
				: AiAnalysisFailureCode.AI_CONNECTION_FAILED;
		return new AiAnalysisException(code, "AI 분석 서버 통신에 실패했습니다.", exception);
	}

	private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (causeType.isInstance(current)) return true;
			if (current.getCause() == current) return false;
		}
		return false;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static final class NamedByteArrayResource extends ByteArrayResource {
		private NamedByteArrayResource(byte[] byteArray) {
			super(byteArray);
		}

		@Override
		public String getFilename() {
			return "image";
		}
	}
}
