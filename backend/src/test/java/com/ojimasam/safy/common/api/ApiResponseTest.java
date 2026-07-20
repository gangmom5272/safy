package com.ojimasam.safy.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
	@Test
	void wrapsDataInSuccessfulResponse() {
		ApiResponse<String> response = ApiResponse.success("safy");

		assertThat(response.success()).isTrue();
		assertThat(response.data()).isEqualTo("safy");
	}

	@Test
	void createsSuccessfulResponseWithoutData() {
		ApiResponse<Void> response = ApiResponse.successWithoutData();

		assertThat(response.success()).isTrue();
		assertThat(response.data()).isNull();
	}
}
