package com.ojimasam.safy.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportType;

class ReportTest {
	@Test
	void createsImageReportInReceivedState() {
		Report report = Report.create(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION,
				"출구 주변이 혼잡합니다.",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				"서울특별시 중구"
		);

		assertThat(report.getReporterId()).isEqualTo(1L);
		assertThat(report.getType()).isEqualTo(ReportType.IMAGE);
		assertThat(report.getCategory()).isEqualTo(ReportCategory.CROWD_CONGESTION);
		assertThat(report.getSubcategory()).isEqualTo(ReportSubcategory.EXIT_CONGESTION);
		assertThat(report.getDescription()).isEqualTo("출구 주변이 혼잡합니다.");
		assertThat(report.getLatitude()).isEqualByComparingTo("37.5665000");
		assertThat(report.getLongitude()).isEqualByComparingTo("126.9780000");
		assertThat(report.getAddress()).isEqualTo("서울특별시 중구");
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(report.getReportedAt()).isNotNull();
	}

	@Test
	void rejectsBlankDescription() {
		assertThatThrownBy(() -> Report.create(
				1L,
				ReportType.LIVE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.MOVEMENT_DIFFICULTY,
				"  ",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				null
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("제보 내용은 비어 있을 수 없습니다.");
	}

	@Test
	void rejectsMissingReporterOrCoordinates() {
		assertThatThrownBy(() -> Report.create(
				null,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.CROWD_INCREASING,
				"혼잡합니다.",
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				null
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("제보자 ID는 필수입니다.");

		assertThatThrownBy(() -> Report.create(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.CROWD_INCREASING,
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
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FACILITY_ROAD, ReportSubcategory.ROAD_DAMAGE, "제보 내용",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);

		report.cancel();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CANCELLED);
			assertThatThrownBy(report::cancel)
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("접수 또는 배정 상태의 제보만 취소할 수 있습니다.");
	}

	@Test
	void cancellableOnlyBeforeOperatorBeginsHandling() {
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FACILITY_ROAD, ReportSubcategory.ROAD_DAMAGE, "제보 내용",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);

		assertThat(report.isCancellable()).isTrue();
		report.assign();
		assertThat(report.isCancellable()).isTrue();
		report.beginHandling();
		assertThat(report.isCancellable()).isFalse();
	}

	@Test
	void rejectsSubcategoryFromAnotherCategory() {
		assertThatThrownBy(() -> Report.create(
				1L, ReportType.LIVE, ReportCategory.ACCIDENT_RISK, ReportSubcategory.EXIT_CONGESTION,
				"사고 현장", new BigDecimal("37.5"), new BigDecimal("127.0"), null
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("제보 카테고리와 세부 상황이 일치하지 않습니다.");
	}
}
