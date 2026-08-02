package com.ojimasam.safy.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ojimasam.safy.analysis.application.dto.CongestionSource;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisResult;
import com.ojimasam.safy.analysis.application.dto.FrameAnalysisStatus;

class AiAnalysisClientTest {
	private static final String BASE_URL = "http://localhost:8000";
	private static final String ANALYZE_URL = BASE_URL + "/api/v1/analyze/frame";
	private static final byte[] IMAGE = "fake-jpeg".getBytes();

	@Test
	void sendsImageMultipartAndReadsCompletedWithZeroPeople() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ANALYZE_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(containsString("name=\"image\"")))
				.andExpect(content().string(containsString("Content-Type: image/jpeg")))
				.andRespond(withSuccess(response("COMPLETED", "0", "[]", "[]"), MediaType.APPLICATION_JSON));

		FrameAnalysisResult result = new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg");

		server.verify();
		assertThat(result.analysisStatus()).isEqualTo(FrameAnalysisStatus.COMPLETED);
		assertThat(result.personCount()).isZero();
		assertThat(result.congestionSource()).isEqualTo(CongestionSource.YOLO);
		assertThat(result.frameQualityRuleVersion()).isEqualTo("frame-quality-v0.3");
	}

	@Test
	void doesNotRetryInvalidFrame() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ANALYZE_URL))
				.andRespond(withSuccess(
						response("INVALID_FRAME", "null", "[\"TOO_DARK\"]", "[]"),
						MediaType.APPLICATION_JSON));

		FrameAnalysisResult result = new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg");

		server.verify();
		assertThat(result.analysisStatus()).isEqualTo(FrameAnalysisStatus.INVALID_FRAME);
		assertThat(result.personCount()).isNull();
	}

	@Test
	void retriesFailedBodyOnce() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ANALYZE_URL))
				.andRespond(withSuccess(
						response("FAILED", "null", "[]", "[\"MODEL_INFERENCE_FAILED\"]"),
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(ANALYZE_URL))
				.andRespond(withSuccess(response("COMPLETED", "3", "[]", "[]"), MediaType.APPLICATION_JSON));

		FrameAnalysisResult result = new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg");

		server.verify();
		assertThat(result.analysisStatus()).isEqualTo(FrameAnalysisStatus.COMPLETED);
		assertThat(result.personCount()).isEqualTo(3);
	}

	@Test
	void returnsFailedAfterOneRetryAlsoFails() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(twice(), requestTo(ANALYZE_URL))
				.andRespond(withSuccess(
						response("FAILED", "null", "[]", "[\"INTERNAL_AI_ERROR\"]"),
						MediaType.APPLICATION_JSON));

		FrameAnalysisResult result = new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg");

		server.verify();
		assertThat(result.analysisStatus()).isEqualTo(FrameAnalysisStatus.FAILED);
	}

	@Test
	void doesNotRetryBadRequest() {
		assertRejectedWithoutRetry(HttpStatus.BAD_REQUEST, AiAnalysisFailureCode.EMPTY_IMAGE);
	}

	@Test
	void doesNotRetryUnsupportedMediaType() {
		assertRejectedWithoutRetry(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE, AiAnalysisFailureCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void retriesConnectionFailureOnce() {
		assertCommunicationFailure(new ConnectException("connection refused"),
				AiAnalysisFailureCode.AI_CONNECTION_FAILED);
	}

	@Test
	void retriesTimeoutOnce() {
		assertCommunicationFailure(new SocketTimeoutException("read timed out"),
				AiAnalysisFailureCode.AI_TIMEOUT);
	}

	@Test
	void rejectsResponseWhoseStatusFieldsAreInconsistent() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ANALYZE_URL))
				.andRespond(withSuccess(response("FAILED", "null", "[]", "[]"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg"))
				.isInstanceOfSatisfying(AiAnalysisException.class,
						exception -> assertThat(exception.getFailureCode())
								.isEqualTo(AiAnalysisFailureCode.INVALID_AI_RESPONSE));
		server.verify();
	}

	private void assertRejectedWithoutRetry(HttpStatus status, AiAnalysisFailureCode expectedCode) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(ANALYZE_URL)).andRespond(withStatus(status));

		assertThatThrownBy(() -> new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg"))
				.isInstanceOfSatisfying(AiAnalysisException.class,
						exception -> assertThat(exception.getFailureCode()).isEqualTo(expectedCode));
		server.verify();
	}

	private void assertCommunicationFailure(IOException failure, AiAnalysisFailureCode expectedCode) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(twice(), requestTo(ANALYZE_URL)).andRespond(request -> { throw failure; });

		assertThatThrownBy(() -> new AiAnalysisClient(builder.build()).analyze(IMAGE, "image/jpeg"))
				.isInstanceOfSatisfying(AiAnalysisException.class,
						exception -> assertThat(exception.getFailureCode()).isEqualTo(expectedCode));
		server.verify();
	}

	private String response(String status, String personCount, String qualityIssues, String failureCodes) {
		return """
				{
				  "analysisStatus": "%s",
				  "personCount": %s,
				  "personConfidences": [],
				  "visualCongestionScore": %s,
				  "localPeakCongestionScore": %s,
				  "congestionSource": "YOLO",
				  "densityLevel": null,
				  "riskScore": null,
				  "confidence": null,
				  "qualityIssues": %s,
				  "failureCodes": %s,
				  "frameWidth": 1280,
				  "frameHeight": 720,
				  "brightnessScore": 86.33,
				  "blurScore": 2364.53,
				  "modelName": "YOLO",
				  "modelVersion": "yolo11n",
				  "modelWeightId": "yolo11n.pt",
				  "congestionRuleVersion": "yolo-grid-v0.2",
				  "frameQualityRuleVersion": "frame-quality-v0.3",
				  "processingTimeMs": 89
				}
				""".formatted(
				status,
				personCount,
				"COMPLETED".equals(status) ? "5.62" : "null",
				"COMPLETED".equals(status) ? "30.0" : "null",
				qualityIssues,
				failureCodes);
	}
}
