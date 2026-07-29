package com.ojimasam.safy.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.type.ReportStatus;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaReportRepositoryTest {
	@Autowired
	private JpaReportRepository reportRepository;

	@Test
	void savesAndLoadsImageReport() {
		Report saved = reportRepository.saveAndFlush(
				Report.create(
						1L,
						com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
						com.ojimasam.safy.report.domain.type.ReportCategory.CROWD_CONGESTION,
						com.ojimasam.safy.report.domain.type.ReportSubcategory.EXIT_CONGESTION,
						"출구 주변이 혼잡합니다.",
						new BigDecimal("37.5665000"),
						new BigDecimal("126.9780000"),
						"서울특별시 중구"
				)
		);

		Report found = reportRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getReporterId()).isEqualTo(1L);
		assertThat(found.getCategory()).isEqualTo(
				com.ojimasam.safy.report.domain.type.ReportCategory.CROWD_CONGESTION);
		assertThat(found.getSubcategory()).isEqualTo(
				com.ojimasam.safy.report.domain.type.ReportSubcategory.EXIT_CONGESTION);
		assertThat(found.getLatitude()).isEqualByComparingTo("37.5665000");
		assertThat(found.getLongitude()).isEqualByComparingTo("126.9780000");
		assertThat(found.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(found.getCreatedAt()).isNotNull();
	}
}
