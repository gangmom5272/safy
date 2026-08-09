package com.ojimasam.safy.controlcenter.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ojimasam.safy.common.config.CorsProperties;
import com.ojimasam.safy.controlcenter.application.dto.ControlCenterResponse;
import com.ojimasam.safy.controlcenter.application.ControlCenterService;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterType;

@WebMvcTest(
		controllers = ControlCenterController.class,
		properties = "spring.autoconfigure.exclude="
				+ "org.springframework.boot.security.oauth2.client.autoconfigure.servlet."
				+ "OAuth2ClientWebSecurityAutoConfiguration",
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.REGEX,
				pattern = "com\\.ojimasam\\.safy\\.auth\\..*"
		)
)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(CorsProperties.class)
class ControlCenterControllerTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean ControlCenterService controlCenterService;

	@Test
	void createsControlCenter() throws Exception {
		Instant now = Instant.parse("2026-07-23T00:00:00Z");
		when(controlCenterService.create(any())).thenReturn(new ControlCenterResponse(
				10L,
				"CC-1168000000",
				"강남구 재난안전상황실",
				ControlCenterType.DISTRICT,
				ControlCenterStatus.ACTIVE,
				null,
				null,
				null,
				now,
				now
		));

		mockMvc.perform(post("/api/v1/admin/control-centers")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "CC-1168000000",
								  "name": "강남구 재난안전상황실",
								  "centerType": "DISTRICT"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/admin/control-centers/10"))
				.andExpect(jsonPath("$.data.code").value("CC-1168000000"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}
}
