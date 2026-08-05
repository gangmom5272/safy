package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.CoverageRole;
import com.ojimasam.safy.controlcenter.domain.type.CoverageStatus;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportCenterRoute;
import com.ojimasam.safy.report.domain.ReportCenterRouteRepository;
import com.ojimasam.safy.report.domain.type.ReportCenterRouteStatus;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;

@Service
public class ReportRoutingTransactionService {
	private final ReportRepository reportRepository;
	private final AdministrativeAreaRepository administrativeAreaRepository;
	private final ControlCenterAreaRepository controlCenterAreaRepository;
	private final ReportCenterRouteRepository routeRepository;
	private final ReportRoutingProperties properties;
	private final ReportOperatorAssignmentService assignmentService;
	private final Clock clock;

	@Autowired
	public ReportRoutingTransactionService(
			ReportRepository reportRepository,
			AdministrativeAreaRepository administrativeAreaRepository,
			ControlCenterAreaRepository controlCenterAreaRepository,
			ReportCenterRouteRepository routeRepository,
			ReportRoutingProperties properties,
			ReportOperatorAssignmentService assignmentService
	) {
		this(reportRepository, administrativeAreaRepository, controlCenterAreaRepository, routeRepository,
				properties, assignmentService, Clock.systemUTC());
	}

	ReportRoutingTransactionService(
			ReportRepository reportRepository,
			AdministrativeAreaRepository administrativeAreaRepository,
			ControlCenterAreaRepository controlCenterAreaRepository,
			ReportCenterRouteRepository routeRepository,
			ReportRoutingProperties properties,
			ReportOperatorAssignmentService assignmentService,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.controlCenterAreaRepository = controlCenterAreaRepository;
		this.routeRepository = routeRepository;
		this.properties = properties;
		this.assignmentService = assignmentService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<ReportRoutingTarget> findPendingTarget(Long reportId) {
		return reportRepository.findOneById(reportId)
				.filter(report -> report.getRoutingStatus() == ReportRoutingStatus.PENDING)
				.map(report -> new ReportRoutingTarget(
						report.getId(),
						report.getLatitude(),
						report.getLongitude(),
						report.getAddress() != null && !report.getAddress().isBlank()
				));
	}

	@Transactional(readOnly = true)
	public List<Long> findDueReportIds() {
		return reportRepository.findPendingRoutingIds(
				ReportRoutingStatus.PENDING,
				clock.instant(),
				PageRequest.of(0, properties.batchSize())
		);
	}

	@Transactional
	public void completeAutomaticRouting(Long reportId, ResolvedAdministrativeArea resolvedArea) {
		Report report = pendingReportForUpdate(reportId);
		if (report == null) return;
		AdministrativeArea area = administrativeAreaRepository
				.findByCodeAndStatus(resolvedArea.code(), AdministrativeAreaStatus.ACTIVE)
				.orElse(null);
		if (area == null) {
			report.requireManualRouting("판별된 행정구역 코드가 등록되어 있지 않습니다: " + resolvedArea.code());
			return;
		}

		Instant now = clock.instant();
		List<ControlCenterArea> candidates = controlCenterAreaRepository.findRoutingCandidates(
				area.getId(),
				now,
				CoverageStatus.ACTIVE,
				ControlCenterStatus.ACTIVE,
				CoverageRole.PRIMARY
		);
		List<ControlCenterArea> primaryCandidates = candidates.stream()
				.filter(candidate -> candidate.getCoverageRole() == CoverageRole.PRIMARY)
				.toList();
		if (primaryCandidates.isEmpty()) {
			report.requireManualRouting("담당 관제센터가 설정되지 않았습니다: " + area.getCode());
			return;
		}

		// 자동 라우팅은 제보 행정구역의 PRIMARY 관제센터에만 맡긴다. BACKUP 센터를
		// 자동 선택하면 송파구 제보가 강남구 담당자에게 보이는 등 관할이 섞인다.
		ControlCenterArea selected = primaryCandidates.stream()
				.filter(this::hasCapacity)
				.findFirst()
				.orElse(null);
		if (selected == null) {
			scheduleRetry(report, "담당 관제센터의 수용 한도가 초과되었습니다.");
			return;
		}

		ControlCenter center = selected.getControlCenter();
		String reason = "AUTO area=" + area.getCode()
				+ ", coverage=" + selected.getCoverageRole()
				+ ", coverageId=" + selected.getId();
		report.completeRouting(area);
		ReportCenterRoute route = routeRepository.save(ReportCenterRoute.createInitial(
				report,
				center,
				selected.getPriority(),
				reason,
				now
		));
		assignmentService.assignAutomatically(report, route);
	}

	/**
	 * 라우팅 성공 여부와 분리해 주소만 저장한다.
	 * 서울 밖 좌표처럼 라우팅이 불가능한 제보에도 주소는 남겨 관제사가 위치를 읽을 수 있게 한다.
	 */
	@Transactional
	public void applyResolvedAddress(Long reportId, String address) {
		reportRepository.findByIdForUpdate(reportId)
				.ifPresent(report -> report.applyResolvedAddress(address));
	}

	@Transactional
	public void markUnresolvable(Long reportId, String reason) {
		Report report = pendingReportForUpdate(reportId);
		if (report != null) report.requireManualRouting(reason);
	}

	@Transactional
	public void recordTransientFailure(Long reportId, String reason) {
		Report report = pendingReportForUpdate(reportId);
		if (report != null) scheduleRetry(report, reason);
	}

	private Report pendingReportForUpdate(Long reportId) {
		Report report = reportRepository.findByIdForUpdate(reportId).orElse(null);
		if (report == null || report.getRoutingStatus() != ReportRoutingStatus.PENDING) return null;
		return report;
	}

	private boolean hasCapacity(ControlCenterArea candidate) {
		ControlCenter center = candidate.getControlCenter();
		Integer capacityLimit = center.getCapacityLimit();
		if (capacityLimit == null) return true;
		long activeRoutes = routeRepository.countByControlCenter_IdAndStatus(
				center.getId(),
				ReportCenterRouteStatus.ACTIVE
		);
		return activeRoutes < capacityLimit;
	}

	private void scheduleRetry(Report report, String reason) {
		Duration delay = properties.retryDelay().multipliedBy(Math.max(1, report.getRoutingAttempts() + 1L));
		report.scheduleRoutingRetry(reason, clock.instant().plus(delay), properties.maxAttempts());
	}
}
