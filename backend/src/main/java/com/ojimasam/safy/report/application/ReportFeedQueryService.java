package com.ojimasam.safy.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.domain.ReportStatus;
import com.ojimasam.safy.report.domain.ReportType;

@Service
@Transactional(readOnly = true)
public class ReportFeedQueryService {
	public static final List<ReportStatus> VISIBLE_STATUSES = Arrays.asList(
			ReportStatus.RECEIVED,
			ReportStatus.ASSIGNED,
			ReportStatus.ACCEPTED,
			ReportStatus.IN_PROGRESS
	);

	private static final Instant MAX_CURSOR = Instant.parse("9999-12-31T23:59:59.999999Z");

	private final ReportRepository reportRepository;
	private final LiveSessionRepository liveSessionRepository;
	private final Clock clock;

	@Autowired
	public ReportFeedQueryService(ReportRepository reportRepository, LiveSessionRepository liveSessionRepository) {
		this(reportRepository, liveSessionRepository, Clock.systemUTC());
	}

	ReportFeedQueryService(
			ReportRepository reportRepository,
			LiveSessionRepository liveSessionRepository,
			Clock clock
	) {
		this.reportRepository = reportRepository;
		this.liveSessionRepository = liveSessionRepository;
		this.clock = clock;
	}

	public ReportCursorResponse recent(Instant cursor, int size) {
		List<Report> reports = reportRepository.findByStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				VISIBLE_STATUSES,
				cursorOrMax(cursor),
				PageRequest.of(0, checkedPageSize(size) + 1)
		);
		return cursorResponse(reports, size);
	}

	public ReportCursorResponse live(Instant cursor, int size) {
		List<Report> reports = reportRepository.findByTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
				ReportType.LIVE,
				VISIBLE_STATUSES,
				cursorOrMax(cursor),
				PageRequest.of(0, checkedPageSize(size) + 1)
		);
		return cursorResponse(reports, size);
	}

	public ReportCursorResponse liveByArea(Long areaId, Instant cursor, int size) {
		List<Report> reports = reportRepository
				.findByAdministrativeArea_IdAndTypeAndStatusInAndReportedAtBeforeOrderByReportedAtDescIdDesc(
						areaId,
						ReportType.LIVE,
						VISIBLE_STATUSES,
						cursorOrMax(cursor),
						PageRequest.of(0, checkedPageSize(size) + 1)
				);
		return cursorResponse(reports, size);
	}

	public ReportAnalysisResponse analysis(Long reportId) {
		reportRepository.findOneById(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return ReportAnalysisResponse.pending(reportId);
	}

	public ReportWebRtcResponse webrtc(Long reportId) {
		reportRepository.findOneById(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		LiveSession session = liveSessionRepository.findByReportId(reportId).orElse(null);
		return ReportWebRtcResponse.from(reportId, session, clock.instant());
	}

	public MyReportSummaryResponse mySummary(Long reporterId) {
		return new MyReportSummaryResponse(
				reportRepository.countByReporterId(reporterId),
				reportRepository.countByReporterIdAndStatusIn(reporterId, VISIBLE_STATUSES),
				reportRepository.countByReporterIdAndStatusIn(reporterId, List.of(ReportStatus.CLOSED))
		);
	}

	private ReportCursorResponse cursorResponse(List<Report> reports, int size) {
		boolean hasNext = reports.size() > size;
		List<Report> page = hasNext ? reports.subList(0, size) : reports;
		Map<Long, LiveSession> liveSessionsByReportId = liveSessionsByReportId(page);
		List<ReportFeedItemResponse> content = page.stream()
				.map(report -> ReportFeedItemResponse.from(report, liveSessionsByReportId.get(report.getId())))
				.toList();
		Instant nextCursor = hasNext && !page.isEmpty() ? page.get(page.size() - 1).getReportedAt() : null;
		return new ReportCursorResponse(content, nextCursor, hasNext);
	}

	private Map<Long, LiveSession> liveSessionsByReportId(List<Report> reports) {
		List<Long> liveReportIds = reports.stream()
				.filter(report -> report.getType() == ReportType.LIVE)
				.map(Report::getId)
				.toList();
		if (liveReportIds.isEmpty()) return Map.of();
		Map<Long, LiveSession> sessions = new HashMap<>();
		for (LiveSession session : liveSessionRepository.findAllByReportIdIn(liveReportIds)) {
			sessions.put(session.getReportId(), session);
		}
		return sessions;
	}

	private Instant cursorOrMax(Instant cursor) {
		return cursor == null ? MAX_CURSOR : cursor;
	}

	private int checkedPageSize(int size) {
		if (size < 1 || size > 100) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "size는 1 이상 100 이하여야 합니다.");
		}
		return size;
	}

	public long countActiveReports(Long areaId) {
		return reportRepository.countByAdministrativeArea_IdAndStatusIn(areaId, VISIBLE_STATUSES);
	}

	public long countActiveReports(Long areaId, Collection<ReportStatus> statuses) {
		return reportRepository.countByAdministrativeArea_IdAndStatusIn(areaId, statuses);
	}

	public long countActiveLiveReports(Long areaId) {
		return reportRepository.countByAdministrativeArea_IdAndTypeAndStatusIn(
				areaId,
				ReportType.LIVE,
				VISIBLE_STATUSES
		);
	}
}
