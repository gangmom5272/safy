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
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ojimasam.safy.report.application.CreateImageReportCommand;
import com.ojimasam.safy.report.application.CreateImageReportService;
import com.ojimasam.safy.report.application.CreateReportResult;
import com.ojimasam.safy.report.application.CreateTextReportCommand;
import com.ojimasam.safy.report.application.CreateTextReportService;
import com.ojimasam.safy.report.application.ReportDetailView;
import com.ojimasam.safy.report.application.ReportListItemView;
import com.ojimasam.safy.report.application.ReportQueryService;
import com.ojimasam.safy.report.application.CancelReportService;
import com.ojimasam.safy.common.config.CorsProperties;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;

@WebMvcTest(
		controllers = ReportController.class,
		properties = "spring.autoconfigure.exclude="
				+ "org.springframework.boot.security.oauth2.client.autoconfigure.servlet."
				+ "OAuth2ClientWebSecurityAutoConfiguration",
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.REGEX,
				pattern = "com\\.ojimasam\\.safy\\.domain\\.auth\\..*"
		)
)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(CorsProperties.class)
class ReportControllerTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean CreateTextReportService createTextReportService;
	@MockitoBean CreateImageReportService createImageReportService;
	@MockitoBean ReportQueryService reportQueryService;
	@MockitoBean CancelReportService cancelReportService;

	@Test
	void cancelsMyReport() throws Exception {
		mockMvc.perform(patch("/api/v1/reports/42/cancel")
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isNoContent());

		verify(cancelReportService).cancel(1L, 42L);
	}

	@Test
	void getsMyReportDetail() throws Exception {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		when(reportQueryService.getMine(1L, 42L)).thenReturn(new ReportDetailView(
				42L, ReportType.TEXT, "제보 내용", new BigDecimal("37.5"), new BigDecimal("127.0"),
				"서울특별시 중구", ReportStatus.RECEIVED, now, now, now, List.of()
		));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reports/42")
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reportId").value(42))
				.andExpect(jsonPath("$.data.images").isArray());
	}

	@Test
	void getsMyReportList() throws Exception {
		Instant now = Instant.parse("2026-07-21T04:00:00Z");
		when(reportQueryService.getMyList(1L, 0, 20)).thenReturn(new PageImpl<>(List.of(
				new ReportListItemView(42L, ReportType.TEXT, "제보 내용", "서울특별시 중구",
						ReportStatus.RECEIVED, now, 0, null)
		)));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reports/me")
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].reportId").value(42));
	}

	@Test
	void returnsUnauthorizedWithoutAuthenticatedUser() throws Exception {
		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content(textBody("TEXT", "제보 내용")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("COMMON_009"));
	}

	@Test
	void createsTextReportFromJson() throws Exception {
		when(createTextReportService.create(any(CreateTextReportCommand.class)))
				.thenReturn(new CreateReportResult(42L, ReportType.TEXT, ReportStatus.RECEIVED,
						Instant.parse("2026-07-21T01:00:00Z")));

		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content(textBody("TEXT", "출구 주변이 혼잡합니다."))
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/reports/42"))
				.andExpect(jsonPath("$.data.reportType").value("TEXT"));

		verify(createTextReportService).create(any(CreateTextReportCommand.class));
	}

	@Test
	void createsImageReportFromMediaAssetIds() throws Exception {
		when(createImageReportService.create(any(CreateImageReportCommand.class)))
				.thenReturn(new CreateReportResult(43L, ReportType.IMAGE, ReportStatus.RECEIVED,
						Instant.parse("2026-07-21T04:00:00Z")));

		mockMvc.perform(post("/api/v1/reports")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "reportType": "IMAGE",
								  "description": "도로가 침수됐습니다.",
								  "latitude": 37.5665,
								  "longitude": 126.978,
								  "address": "서울특별시 중구",
								  "mediaAssetIds": [10, 11]
								}
								""")
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.reportId").value(43))
				.andExpect(jsonPath("$.data.reportType").value("IMAGE"));
	}

	@Test
	void rejectsTextReportWithMediaAssets() throws Exception {
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
						.requestAttr(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, 1L))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON_001"));
	}

	private String textBody(String reportType, String description) {
		return """
				{
				  "reportType": "%s",
				  "description": "%s",
				  "latitude": 37.5665,
				  "longitude": 126.978,
				  "address": "서울특별시 중구"
				}
				""".formatted(reportType, description);
	}
}
