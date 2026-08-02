package com.ojimasam.safy.analysis.infrastructure;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;

@Component
public class AiBlurClient {
	private static final String BLUR_PATH = "/api/v1/privacy/blur";
	private static final int MAX_ATTEMPTS = 2;

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public AiBlurClient(@Qualifier("aiAnalysisRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	public BlurredImageResult blur(byte[] image, String contentType) {
		if (image == null || image.length == 0) throw new IllegalArgumentException("블러 처리할 이미지가 필요합니다.");
		MediaType mediaType = MediaType.parseMediaType(contentType);
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return request(image, mediaType);
			} catch (HttpStatusCodeException exception) {
				throw rejectedRequest(exception);
			} catch (ResourceAccessException exception) {
				if (attempt == MAX_ATTEMPTS) throw communicationFailure(exception);
			} catch (AiBlurException exception) {
				throw exception;
			} catch (RestClientException exception) {
				throw new AiBlurException(AiBlurFailureCode.INVALID_AI_RESPONSE,
						"AI 블러 요청에 실패했습니다.", exception);
			}
		}
		throw new IllegalStateException("AI 블러 재시도 흐름이 종료되지 않았습니다.");
	}

	private BlurredImageResult request(byte[] image, MediaType mediaType) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("image", new ImageResource(image)).contentType(mediaType);
		ResponseEntity<byte[]> response = restClient.post()
				.uri(BLUR_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body.build())
				.retrieve()
				.toEntity(byte[].class);
		byte[] blurred = response.getBody();
		if (blurred == null || blurred.length == 0
				|| !MediaType.IMAGE_JPEG.isCompatibleWith(response.getHeaders().getContentType())) {
			throw new AiBlurException(AiBlurFailureCode.INVALID_AI_RESPONSE, "AI 블러 응답이 올바르지 않습니다.");
		}
		return new BlurredImageResult(
				blurred,
				requiredHeader(response, "X-Blur-Rule-Version"),
				integerHeader(response, "X-Image-Width"),
				integerHeader(response, "X-Image-Height"),
				integerHeader(response, "X-Processing-Time-Ms"));
	}

	private String requiredHeader(ResponseEntity<byte[]> response, String name) {
		String value = response.getHeaders().getFirst(name);
		if (value == null || value.isBlank()) {
			throw new AiBlurException(AiBlurFailureCode.INVALID_AI_RESPONSE, "AI 블러 응답 헤더가 누락되었습니다: " + name);
		}
		return value;
	}

	private int integerHeader(ResponseEntity<byte[]> response, String name) {
		try {
			return Integer.parseInt(requiredHeader(response, name));
		} catch (NumberFormatException exception) {
			throw new AiBlurException(AiBlurFailureCode.INVALID_AI_RESPONSE, "AI 블러 응답 헤더가 올바르지 않습니다: " + name, exception);
		}
	}

	private AiBlurException rejectedRequest(HttpStatusCodeException exception) {
		AiBlurFailureCode code = responseFailureCode(exception);
		return new AiBlurException(code, "AI 블러 요청이 거부되었습니다: HTTP "
				+ exception.getStatusCode().value(), exception);
	}

	private AiBlurFailureCode responseFailureCode(HttpStatusCodeException exception) {
		try {
			BlurFailureResponse response = objectMapper.readValue(
					exception.getResponseBodyAsByteArray(), BlurFailureResponse.class);
			if (response.failureCodes() != null && !response.failureCodes().isEmpty()) {
				return AiBlurFailureCode.valueOf(response.failureCodes().getFirst());
			}
		} catch (Exception ignored) {
			// 형식이 계약과 다르면 상태 코드 기반 기본값 또는 INVALID_AI_RESPONSE를 사용한다.
		}
		return switch (exception.getStatusCode().value()) {
			case 400 -> AiBlurFailureCode.EMPTY_IMAGE;
			case 415 -> AiBlurFailureCode.UNSUPPORTED_MEDIA_TYPE;
			case 422 -> AiBlurFailureCode.IMAGE_DECODE_FAILED;
			default -> AiBlurFailureCode.INVALID_AI_RESPONSE;
		};
	}

	private AiBlurException communicationFailure(ResourceAccessException exception) {
		AiBlurFailureCode code = hasCause(exception, SocketTimeoutException.class)
				|| hasCause(exception, HttpTimeoutException.class)
				? AiBlurFailureCode.AI_TIMEOUT
				: AiBlurFailureCode.AI_CONNECTION_FAILED;
		return new AiBlurException(code, "AI 블러 서버 통신에 실패했습니다.", exception);
	}

	private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (causeType.isInstance(current)) return true;
			if (current.getCause() == current) return false;
		}
		return false;
	}

	private record BlurFailureResponse(List<String> failureCodes) { }

	private static final class ImageResource extends ByteArrayResource {
		private ImageResource(byte[] bytes) { super(bytes); }
		@Override public String getFilename() { return "image"; }
	}
}
