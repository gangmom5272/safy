package com.ojimasam.safy.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ojimasam.safy.analysis.application.dto.BlurredImageResult;

class AiBlurClientTest {
	@Test
	void sendsImageAndReadsJpegWithMetadataHeaders() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://localhost:8000/api/v1/privacy/blur"))
				.andExpect(content().string(containsString("name=\"image\"")))
				.andExpect(content().string(containsString("Content-Type: image/png")))
				.andRespond(withSuccess("blurred-jpeg".getBytes(), MediaType.IMAGE_JPEG)
						.header("X-Blur-Rule-Version", "full-image-blur-v0.1")
						.header("X-Image-Width", "1280")
						.header("X-Image-Height", "720")
						.header("X-Processing-Time-Ms", "24"));

		BlurredImageResult result = new AiBlurClient(builder.build())
				.blur("original".getBytes(), MediaType.IMAGE_PNG_VALUE);

		server.verify();
		assertThat(result.image()).isEqualTo("blurred-jpeg".getBytes());
		assertThat(result.ruleVersion()).isEqualTo("full-image-blur-v0.1");
		assertThat(result.width()).isEqualTo(1280);
		assertThat(result.height()).isEqualTo(720);
		assertThat(result.processingTimeMs()).isEqualTo(24);
	}

	@Test
	void readsBlurFailureCodeFromErrorResponseWithoutRetry() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://localhost:8000/api/v1/privacy/blur"))
				.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"failureCodes\":[\"IMAGE_DECODE_FAILED\"]}"));

		assertThatThrownBy(() -> new AiBlurClient(builder.build())
				.blur("broken".getBytes(), MediaType.IMAGE_JPEG_VALUE))
				.isInstanceOfSatisfying(AiBlurException.class, exception ->
						assertThat(exception.getFailureCode()).isEqualTo(AiBlurFailureCode.IMAGE_DECODE_FAILED));
		server.verify();
	}

	@Test
	void retriesConnectionFailureOnce() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(twice(), requestTo("http://localhost:8000/api/v1/privacy/blur"))
				.andRespond(request -> { throw new ConnectException("connection refused"); });

		assertThatThrownBy(() -> new AiBlurClient(builder.build())
				.blur("image".getBytes(), MediaType.IMAGE_JPEG_VALUE))
				.isInstanceOfSatisfying(AiBlurException.class, exception ->
						assertThat(exception.getFailureCode()).isEqualTo(AiBlurFailureCode.AI_CONNECTION_FAILED));
		server.verify();
	}
}
