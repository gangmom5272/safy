package com.ojimasam.safy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CorsConfigTest {
	@Test
	void allowsPutForLocationPresencePreflight() {
		var source = new CorsConfig().corsConfigurationSource(
				new CorsProperties(List.of("http://localhost:5173")));
		var request = new MockHttpServletRequest("OPTIONS", "/api/v1/users/me/location-presence");

		var configuration = source.getCorsConfiguration(request);

		assertThat(configuration).isNotNull();
		assertThat(configuration.getAllowedMethods()).contains("PUT");
	}
}
