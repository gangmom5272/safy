package com.ojimasam.safy.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
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
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.PENDING);
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
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
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
	void completingWorkPublishesToCitizenFeed() {
		Report report = handledReport();

		report.completeWork(closedAt(report));

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CLOSED);
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.PUBLISHED);
	}

	@Test
	void rejectingWithholdsFromCitizenFeed() {
		Report report = handledReport();

		report.reject(closedAt(report));

		assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
	}

	/** 내려둔 제보를 담당자가 종료해도 다시 공개되지 않고, 종료 자체는 막히지 않는다. */
	@Test
	void completingWorkKeepsWithheldReportHidden() {
		Report report = handledReport();
		report.withhold();

		report.completeWork(closedAt(report));

		assertThat(report.getStatus()).isEqualTo(ReportStatus.CLOSED);
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
	}

	@Test
	void withholdIsIdempotent() {
		Report report = handledReport();

		report.withhold();
		report.withhold();

		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.WITHHELD);
	}

	@Test
	void reassignmentDoesNotChangeVisibility() {
		Report report = handledReport();

		report.releaseForReassignment();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
		assertThat(report.getPublicVisibility()).isEqualTo(ReportPublicVisibility.PENDING);
	}

	@Test
	void rejectsSubcategoryFromAnotherCategory() {
		assertThatThrownBy(() -> Report.create(
				1L, ReportType.LIVE, ReportCategory.ACCIDENT_RISK, ReportSubcategory.EXIT_CONGESTION,
				"사고 현장", new BigDecimal("37.5"), new BigDecimal("127.0"), null
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("제보 카테고리와 세부 상황이 일치하지 않습니다.");
	}

	@Test
	void assignsGridCellCodeFromCoordinatesOnCreate() {
		Report report = Report.create(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION,
				"홍대입구역 혼잡",
				new BigDecimal("37.5572000"),
				new BigDecimal("126.9254000"),
				null
		);

		assertThat(report.getGridCellCode()).isEqualTo("S100-r174-c199");
	}

	@Test
	void keepsCoordinatesAndGridCellCodeConsistent() {
		// 같은 셀 안에서 조금 움직인 좌표는 같은 셀이 된다.
		Report sameCell = reportAt("37.5573000", "126.9256000");
		// 북쪽으로 100m 옮기면 다른 셀이 된다.
		Report northCell = reportAt("37.5581000", "126.9254000");

		assertThat(sameCell.getGridCellCode()).isEqualTo("S100-r174-c199");
		assertThat(northCell.getGridCellCode()).isEqualTo("S100-r175-c199");
	}

	private Report reportAt(String latitude, String longitude) {
		return Report.create(
				1L,
				ReportType.IMAGE,
				ReportCategory.CROWD_CONGESTION,
				ReportSubcategory.EXIT_CONGESTION,
				"제보 내용",
				new BigDecimal(latitude),
				new BigDecimal(longitude),
				null
		);
	}

	private Report handledReport() {
		Report report = Report.create(1L, ReportType.IMAGE,
				ReportCategory.FACILITY_ROAD, ReportSubcategory.ROAD_DAMAGE, "제보 내용",
				new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		report.assign();
		report.beginHandling();
		return report;
	}

	/** reportedAt 은 생성 시각(now)이라 고정 상수를 쓰면 종료 시각 검증에 걸린다. */
	private Instant closedAt(Report report) {
		return report.getReportedAt().plusSeconds(60);
	}
}
