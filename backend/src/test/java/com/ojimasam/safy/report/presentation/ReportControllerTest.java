package com.ojimasam.safy.report.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ojimasam.safy.report.application.dto.CreateReportCommand;
import com.ojimasam.safy.report.application.dto.CreateReportResult;
import com.ojimasam.safy.report.application.CreateReportService;
import com.ojimasam.safy.report.application.dto.ReportDetailView;
import com.ojimasam.safy.report.application.dto.ReportListItemView;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.CancelReportService;
import com.ojimasam.safy.common.config.CorsProperties;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

@WebMvcTest(
		controllers = ReportController.class,
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
@Import(ReportControllerTest.SecurityArgumentResolverConfig.class)
class ReportControllerTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean CreateReportService createReportService;
	@MockitoBean ReportQueryService reportQueryService;
	@MockitoBean CancelReportService cancelReportService;

	@Test
	void cancelsMyReport() throws Exception {
		mockMvc.perform(patch("/api/v1/reports/42/cancel")
						.with(authenticatedUser(1L)))
				.andExpect(status().isNoContent());

		verify(cancelReportService).cancel(1L, 42L);
	}

	@Test
	void getsMyReportDetail() throws Exception {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		when(reportQueryService.getMine(1L, 42L)).thenReturn(new ReportDetailView(
				42L, ReportType.IMAGE, ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING,
				"제보 내용", new BigDecimal("37.5"), new BigDecimal("127.0"),
				"서울특별시 중구", ReportStatus.RECEIVED, true, ReportRoutingStatus.ROUTED,
				2L, "1114000000", "중구", null, null, null, now, now, now, List.of(), null,
				null, null, null, null
		));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reports/42")
						.with(authenticatedUser(1L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reportId").value(42))
				.andExpect(jsonPath("$.data.images").isArray());
	}

	@Test
	void getsMyReportList() throws Exception {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		when(reportQueryService.getMyList(1L, 0, 20)).thenReturn(new PageImpl<>(List.of(
				new ReportListItemView(42L, ReportType.LIVE,
						ReportCategory.CROWD_CONGESTION, ReportSubcategory.CROWD_INCREASING,
						"제보 내용", "서울특별시 중구",
						ReportStatus.RECEIVED, true, ReportRoutingStatus.ROUTED, "중구", now, 0, null)
		)));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reports/me")
						.with(authenticatedUser(1L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].reportId").value(42));
	}

	@Test
	void createsLiveReportFromJson() throws Exception {
		when(createReportService.create(any(CreateReportCommand.class)))
				.thenReturn(new CreateReportResult(42L, ReportType.LIVE,
						ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION,
						ReportStatus.RECEIVED, ReportRoutingStatus.PENDING,
						Instant.parse("2026-07-21T01:00:00Z")));

		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content(reportBody("LIVE", "출구 주변이 혼잡합니다."))
						.with(authenticatedUser(1L)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/reports/42"))
				.andExpect(jsonPath("$.data.reportType").value("LIVE"));

		verify(createReportService).create(any(CreateReportCommand.class));
	}

	@Test
	void createsImageReportWithoutMediaAssets() throws Exception {
		when(createReportService.create(any(CreateReportCommand.class)))
				.thenReturn(new CreateReportResult(43L, ReportType.IMAGE,
						ReportCategory.FLOOD_WEATHER, ReportSubcategory.ROAD_FLOODING,
						ReportStatus.RECEIVED, ReportRoutingStatus.PENDING,
						Instant.parse("2026-07-21T04:00:00Z")));

		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "reportType": "IMAGE",
								  "category": "FLOOD_WEATHER",
								  "subcategory": "ROAD_FLOODING",
								  "description": "도로가 침수됐습니다.",
								  "latitude": 37.5665,
								  "longitude": 126.978
								}
								""")
						.with(authenticatedUser(1L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.reportId").value(43))
				.andExpect(jsonPath("$.data.reportType").value("IMAGE"));
	}

	@Test
	void rejectsRemovedTextReportType() throws Exception {
		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "reportType": "TEXT",
								  "description": "제보 내용",
								  "latitude": 37.5,
								  "longitude": 127.0,
								  "mediaAssetIds": [10]
								}
								""")
						.with(authenticatedUser(1L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_002"));
	}

	private String reportBody(String reportType, String description) {
		return """
				{
				  "reportType": "%s",
				  "category": "CROWD_CONGESTION",
				  "subcategory": "EXIT_CONGESTION",
				  "description": "%s",
				  "latitude": 37.5665,
				  "longitude": 126.978
				}
				""".formatted(reportType, description);
	}

	private RequestPostProcessor authenticatedUser(Long userId) {
		return request -> {
			Jwt jwt = Jwt.withTokenValue("test-access-token")
					.header("alg", "HS256")
					.subject(userId.toString())
					.build();
			JwtAuthenticationToken authentication = new JwtAuthenticationToken(
					jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN")), userId.toString());
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			request.setUserPrincipal(authentication);
			return request;
		};
	}

	@TestConfiguration
	static class SecurityArgumentResolverConfig implements WebMvcConfigurer {
		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
			resolvers.add(new AuthenticationPrincipalArgumentResolver());
		}
	}
}
