package com.ojimasam.safy.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterType;
import com.ojimasam.safy.report.application.OperatorReportAccessService;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.type.ReportPublicVisibility;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaReportRepositoryTest {
	@Autowired
	private JpaReportRepository reportRepository;

	@Autowired
	private TestEntityManager entityManager;

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
		assertThat(found.getPublicVisibility()).isEqualTo(ReportPublicVisibility.PENDING);
		assertThat(found.getRepresentativeBlurAsset()).isNull();
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void publicFeedReturnsOnlyPublishedReportsNewestFirst() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Instant windowStart = now.minus(Duration.ofMinutes(30));
		visibleReport("공개 오래된", ReportPublicVisibility.PUBLISHED, now.minusSeconds(600));
		visibleReport("공개 최신", ReportPublicVisibility.PUBLISHED, now.minusSeconds(60));
		visibleReport("미검수", ReportPublicVisibility.PENDING, now.minusSeconds(30));
		visibleReport("내려짐", ReportPublicVisibility.WITHHELD, now.minusSeconds(10));
		entityManager.flush();

		List<Report> result = reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						ReportPublicVisibility.PUBLISHED, windowStart, now, PageRequest.of(0, 10));

		assertThat(result)
				.extracting(Report::getDescription)
				.containsExactly("공개 최신", "공개 오래된");
	}

	/** 창 밖 제보는 공개되어 있어도 시민 지도에 남지 않아야 한다. */
	@Test
	void publicFeedDropsPublishedReportsOlderThanTheWindow() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Instant windowStart = now.minus(Duration.ofMinutes(30));
		visibleReport("창 안", ReportPublicVisibility.PUBLISHED, now.minus(Duration.ofMinutes(29)));
		visibleReport("창 밖", ReportPublicVisibility.PUBLISHED, now.minus(Duration.ofMinutes(31)));
		entityManager.flush();

		List<Report> result = reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						ReportPublicVisibility.PUBLISHED, windowStart, now, PageRequest.of(0, 10));

		assertThat(result).extracting(Report::getDescription).containsExactly("창 안");
	}

	@Test
	void publicFeedHonoursCursorAndPageSize() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Instant windowStart = now.minus(Duration.ofMinutes(30));
		visibleReport("첫째", ReportPublicVisibility.PUBLISHED, now.minusSeconds(300));
		visibleReport("둘째", ReportPublicVisibility.PUBLISHED, now.minusSeconds(200));
		visibleReport("셋째", ReportPublicVisibility.PUBLISHED, now.minusSeconds(100));
		entityManager.flush();

		List<Report> firstPage = reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						ReportPublicVisibility.PUBLISHED, windowStart, now, PageRequest.of(0, 2));
		List<Report> afterCursor = reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						ReportPublicVisibility.PUBLISHED, windowStart, now.minusSeconds(200), PageRequest.of(0, 2));

		assertThat(firstPage).extracting(Report::getDescription).containsExactly("셋째", "둘째");
		assertThat(afterCursor).extracting(Report::getDescription).containsExactly("첫째");
	}

	/**
	 * 장소 검색의 '최근 제보' 카운터({@code PlaceRecentReportCounter})가 쓰는 조회다.
	 * 행정구역으로 미리 거르지 않고 창 안 공개 제보를 전부 읽어 애플리케이션에서 폴리곤
	 * 판정하므로, 여기서는 공개 여부·창 조건만 정확한지 확인한다.
	 */
	@Test
	void findsAllPublicReportsInWindowRegardlessOfArea() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Instant windowStart = now.minus(Duration.ofMinutes(30));
		visibleReport("창 안 공개", ReportPublicVisibility.PUBLISHED, now.minusSeconds(60));
		visibleReport("창 밖 공개", ReportPublicVisibility.PUBLISHED, now.minus(Duration.ofMinutes(31)));
		visibleReport("창 안 미검수", ReportPublicVisibility.PENDING, now.minusSeconds(60));
		entityManager.flush();

		List<Report> result = reportRepository
				.findByPublicVisibilityAndReportedAtAfter(ReportPublicVisibility.PUBLISHED, windowStart);

		assertThat(result).extracting(Report::getDescription).containsExactly("창 안 공개");
	}

	/**
	 * 좌표 반경 조회의 1차 필터({@code RadiusRecentReportCounter}). 사각형 안이면 잡히고,
	 * 원 밖인지는 애플리케이션이 실제 거리로 다시 판정한다.
	 */
	@Test
	void findsPublicReportsInsideBoundingBox() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Instant windowStart = now.minus(Duration.ofMinutes(30));
		reportAt("사각형 안", ReportPublicVisibility.PUBLISHED, now.minusSeconds(60), "37.5010000", "127.0010000");
		reportAt("사각형 밖", ReportPublicVisibility.PUBLISHED, now.minusSeconds(60), "37.6000000", "127.1000000");
		reportAt("창 밖", ReportPublicVisibility.PUBLISHED, now.minus(Duration.ofMinutes(31)), "37.5010000", "127.0010000");
		reportAt("미검수", ReportPublicVisibility.PENDING, now.minusSeconds(60), "37.5010000", "127.0010000");
		entityManager.flush();

		List<Report> result = reportRepository
				.findByPublicVisibilityAndReportedAtAfterAndLatitudeBetweenAndLongitudeBetween(
						ReportPublicVisibility.PUBLISHED, windowStart,
						new BigDecimal("37.4900000"), new BigDecimal("37.5100000"),
						new BigDecimal("126.9900000"), new BigDecimal("127.0100000"));

		assertThat(result).extracting(Report::getDescription).containsExactly("사각형 안");
	}

	@Test
	void findByIdAndPublicVisibilityHidesUnpublishedReport() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		Report published = visibleReport("공개", ReportPublicVisibility.PUBLISHED, now);
		Report pending = visibleReport("미검수", ReportPublicVisibility.PENDING, now);
		entityManager.flush();

		assertThat(reportRepository.findByIdAndPublicVisibility(
				published.getId(), ReportPublicVisibility.PUBLISHED)).isPresent();
		assertThat(reportRepository.findByIdAndPublicVisibility(
				pending.getId(), ReportPublicVisibility.PUBLISHED)).isEmpty();
	}

	/** 좌표까지 지정하는 변형. bbox 조회 검증에만 쓴다. */
	private Report reportAt(
			String description, ReportPublicVisibility visibility, Instant reportedAt,
			String latitude, String longitude) {
		Report report = Report.create(
				1L,
				com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.CROWD_CONGESTION,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.EXIT_CONGESTION,
				description,
				new BigDecimal(latitude),
				new BigDecimal(longitude),
				"서울특별시"
		);
		ReflectionTestUtils.setField(report, "publicVisibility", visibility);
		ReflectionTestUtils.setField(report, "reportedAt", reportedAt);
		return entityManager.persist(report);
	}

	private Report visibleReport(String description, ReportPublicVisibility visibility, Instant reportedAt) {
		Report report = Report.create(
				1L,
				com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.CROWD_CONGESTION,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.EXIT_CONGESTION,
				description,
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				"서울특별시 중구"
		);
		// 공개 전이는 담당자 종료를 거쳐야 하므로, 조회 조건만 검증하는 여기서는 필드를 직접 세팅한다.
		ReflectionTestUtils.setField(report, "publicVisibility", visibility);
		ReflectionTestUtils.setField(report, "reportedAt", reportedAt);
		return entityManager.persist(report);
	}

	@Test
	void centerQueueReturnsOnlyUnassignedRoutedReportsOfCenterOldestFirst() {
		Instant now = Instant.parse("2026-07-30T00:00:00Z");
		ControlCenter myCenter = entityManager.persist(
				ControlCenter.create("GN", "강남 관제센터", ControlCenterType.DISTRICT, null, null, null));
		ControlCenter otherCenter = entityManager.persist(
				ControlCenter.create("SC", "서초 관제센터", ControlCenterType.DISTRICT, null, null, null));
		User operator = entityManager.persist(User.operator("담당자", UserRole.OPERATOR));

		Report oldUnassigned = routedReport("오래된 미배정", now.minusSeconds(600));
		Report newUnassigned = routedReport("새 미배정", now.minusSeconds(60));
		Report taken = routedReport("배정된 제보", now.minusSeconds(300));
		Report otherCenters = routedReport("다른 센터 제보", now.minusSeconds(300));

		route(oldUnassigned, myCenter, now);
		route(newUnassigned, myCenter, now);
		ReportCenterRoute takenRoute = route(taken, myCenter, now);
		route(otherCenters, otherCenter, now);
		entityManager.persist(ReportOperatorAssignment.automatic(taken, takenRoute, operator, now));
		entityManager.flush();

		var result = reportRepository.findCenterQueue(
				ReportRoutingStatus.ROUTED,
				ReportStatus.RECEIVED,
				myCenter.getId(),
				ReportCenterRouteStatus.ACTIVE,
				List.copyOf(OperatorReportAccessService.ACTIVE_ASSIGNMENT_STATUSES),
				PageRequest.of(0, 10)
		);

		assertThat(result.getContent())
				.extracting(Report::getDescription)
				.containsExactly("오래된 미배정", "새 미배정");
	}

	private Report routedReport(String description, Instant reportedAt) {
		Report report = Report.create(
				1L,
				com.ojimasam.safy.report.domain.type.ReportType.IMAGE,
				com.ojimasam.safy.report.domain.type.ReportCategory.CROWD_CONGESTION,
				com.ojimasam.safy.report.domain.type.ReportSubcategory.EXIT_CONGESTION,
				description,
				new BigDecimal("37.5665000"),
				new BigDecimal("126.9780000"),
				"서울특별시 강남구"
		);
		// 행정구역 엔티티는 SQL 시드 전용이라 라우팅 완료 상태만 직접 만든다.
		ReflectionTestUtils.setField(report, "routingStatus", ReportRoutingStatus.ROUTED);
		ReflectionTestUtils.setField(report, "reportedAt", reportedAt);
		return entityManager.persist(report);
	}

	private ReportCenterRoute route(Report report, ControlCenter center, Instant routedAt) {
		return entityManager.persist(
				ReportCenterRoute.createInitial(report, center, 1, "테스트 라우팅", routedAt));
	}
}
