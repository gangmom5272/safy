package com.ojimasam.safy.report.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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

import com.ojimasam.safy.common.config.CorsProperties;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.application.PublicReportQueryService;
import com.ojimasam.safy.report.application.dto.PublicReportCursorResponse;
import com.ojimasam.safy.report.application.dto.PublicReportView;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

@WebMvcTest(
		controllers = PublicReportFeedController.class,
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
class PublicReportFeedControllerTest {
	private static final Instant REPORTED_AT = Instant.parse("2026-07-31T04:00:00Z");

	@Autowired MockMvc mockMvc;
	@MockitoBean PublicReportQueryService publicReportQueryService;

	/**
	 * 시민 응답에 라이브 세션 정보가 다시 섞이는 것을 막는 회귀 테스트다.
	 * 뷰에 필드를 추가하다 스트림 정보를 되살리면 여기서 깨진다.
	 */
	@Test
	void feedResponseNeverExposesStreamInfo() throws Exception {
		when(publicReportQueryService.reports(any(), eq(20)))
				.thenReturn(new PublicReportCursorResponse(List.of(view()), REPORTED_AT, true));

		mockMvc.perform(get("/api/v1/feed/reports"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].reportId").value(42))
				.andExpect(jsonPath("$.data.content[0].blurredImageUrl").value("http://localhost:9000/blurred"))
				.andExpect(jsonPath("$.data.content[0].streamRoomId").doesNotExist())
				.andExpect(jsonPath("$.data.content[0].streamStatus").doesNotExist())
				.andExpect(jsonPath("$.data.content[0].status").doesNotExist())
				.andExpect(jsonPath("$.data.content[0].reporterId").doesNotExist());
	}

	@Test
	void areaFeedPassesCursorAndSize() throws Exception {
		when(publicReportQueryService.reportsByArea(eq(7L), eq(REPORTED_AT), eq(5)))
				.thenReturn(new PublicReportCursorResponse(List.of(view()), null, false));

		mockMvc.perform(get("/api/v1/feed/areas/7/reports")
						.param("cursor", REPORTED_AT.toString())
						.param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void detailReturnsNotFoundForUnpublishedReport() throws Exception {
		when(publicReportQueryService.report(42L))
				.thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

		mockMvc.perform(get("/api/v1/feed/reports/42"))
				.andExpect(status().isNotFound());
	}

	@Test
	void analysisReturnsNotFoundForUnpublishedReport() throws Exception {
		when(publicReportQueryService.analysis(42L))
				.thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

		mockMvc.perform(get("/api/v1/feed/reports/42/analysis"))
				.andExpect(status().isNotFound());
	}

	private PublicReportView view() {
		return new PublicReportView(
				42L, ReportType.LIVE, ReportCategory.CROWD_CONGESTION, ReportSubcategory.EXIT_CONGESTION,
				"출구 주변이 혼잡합니다.", new BigDecimal("37.5665000"), new BigDecimal("126.9780000"),
				"서울특별시 중구", 1L, "11140", "중구", REPORTED_AT,
				"http://localhost:9000/blurred", REPORTED_AT.plusSeconds(600)
		);
	}
}
