package com.ojimasam.safy.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ReportTest {
	@Test
	void createsTextReportInReceivedState() {
		Report report = Report.createText(
				1L,
				"출구 주변이 혼잡합니다.",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				"서울특별시 중구"
		);

		assertThat(report.getReporterId()).isEqualTo(1L);
		assertThat(report.getType()).isEqualTo(ReportType.TEXT);
		assertThat(report.getDescription()).isEqualTo("출구 주변이 혼잡합니다.");
		assertThat(report.getLatitude()).isEqualByComparingTo("37.5665000");
		assertThat(report.getLongitude()).isEqualByComparingTo("126.9780000");
		assertThat(report.getAddress()).isEqualTo("서울특별시 중구");
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(report.getReportedAt()).isNotNull();
	}

	@Test
	void rejectsBlankTextContent() {
		assertThatThrownBy(() -> Report.createText(
				1L,
				"  ",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				null
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("텍스트 제보 내용은 비어 있을 수 없습니다.");
	}

	@Test
	void rejectsMissingReporterOrCoordinates() {
		assertThatThrownBy(() -> Report.createText(
				null,
				"혼잡합니다.",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				null
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("제보자 ID는 필수입니다.");

		assertThatThrownBy(() -> Report.createText(
				1L,
				"혼잡합니다.",
				null,
				new BigDecimal("126.9780000"),
				null
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("위도와 경도는 필수입니다.");
	}

	@Test
	void cancelsReceivedReportAndRejectsRepeatedCancellation() {
		Report report = Report.createText(1L, "제보 내용", new BigDecimal("37.5"), new BigDecimal("127.0"), null);

		report.cancel();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELLED);
		assertThatThrownBy(report::cancel)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("접수 상태의 제보만 변경할 수 있습니다.");
	}
}
