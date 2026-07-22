package com.ojimasam.safy.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ojimasam.safy.common.config.CorsProperties;

import jakarta.servlet.FilterChain;

class CookieAuthOriginFilterTest {
	private final CookieAuthOriginFilter filter = new CookieAuthOriginFilter(
			new CorsProperties(List.of("http://localhost:3000")));

	@Test
	void rejectsUntrustedBrowserOriginForRefresh() throws Exception {
		MockHttpServletRequest request = request("/api/v1/auth/token/refresh", "https://evil.example");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verifyNoInteractions(chain);
	}

	@Test
	void allowsConfiguredOriginForLogout() throws Exception {
		MockHttpServletRequest request = request("/api/v1/auth/logout", "http://localhost:3000");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	private MockHttpServletRequest request(String path, String origin) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		request.setServletPath(path);
		request.addHeader("Origin", origin);
		return request;
	}
}
