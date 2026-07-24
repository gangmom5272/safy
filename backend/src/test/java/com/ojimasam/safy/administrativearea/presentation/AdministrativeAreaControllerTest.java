package com.ojimasam.safy.administrativearea.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaQueryService;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResponse;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaType;
import com.ojimasam.safy.common.config.CorsProperties;

@WebMvcTest(
		controllers = AdministrativeAreaController.class,
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
class AdministrativeAreaControllerTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean AdministrativeAreaQueryService administrativeAreaQueryService;

	@Test
	void listsDistrictsUsingDefaultAreaType() throws Exception {
		when(administrativeAreaQueryService.list(AdministrativeAreaType.SIGUNGU, "1100000000"))
				.thenReturn(List.of(new AdministrativeAreaResponse(
						2L,
						"1168000000",
						"강남구",
						AdministrativeAreaType.SIGUNGU,
						1L,
						"1100000000"
				)));

		mockMvc.perform(get("/api/v1/administrative-areas")
						.param("parentCode", "1100000000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].code").value("1168000000"))
				.andExpect(jsonPath("$.data[0].name").value("강남구"))
				.andExpect(jsonPath("$.data[0].areaType").value("SIGUNGU"));

		verify(administrativeAreaQueryService).list(AdministrativeAreaType.SIGUNGU, "1100000000");
	}

	@Test
	void getsAreaDetail() throws Exception {
		when(administrativeAreaQueryService.get(2L)).thenReturn(new AdministrativeAreaResponse(
				2L,
				"1168000000",
				"강남구",
				AdministrativeAreaType.SIGUNGU,
				1L,
				"1100000000"
		));

		mockMvc.perform(get("/api/v1/administrative-areas/2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(2))
				.andExpect(jsonPath("$.data.parentCode").value("1100000000"));
	}
}
