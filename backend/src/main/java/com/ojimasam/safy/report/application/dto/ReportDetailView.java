package com.ojimasam.safy.report.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ojimasam.safy.report.domain.type.ReportStatus;
import com.ojimasam.safy.report.domain.type.ReportType;
import com.ojimasam.safy.report.domain.type.ReportCategory;
import com.ojimasam.safy.report.domain.type.ReportSubcategory;
import com.ojimasam.safy.report.domain.type.ReportRoutingStatus;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;

public record ReportDetailView(
		Long reportId,
		ReportType reportType,
		ReportCategory category,
		ReportSubcategory subcategory,
		String description,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		ReportStatus status,
		/** 제보자가 지금 취소할 수 있는지. 판단 규칙은 도메인(Report.isCancellable) 한 곳에만 둔다. */
		boolean cancellable,
		ReportRoutingStatus routingStatus,
		Long administrativeAreaId,
		String administrativeAreaCode,
		String administrativeAreaName,
		AssignedControlCenterView assignedControlCenter,
		/**
		 * 지금 이 제보를 맡고 있는(또는 종료한) 담당자. 담당자 조회에서만 채워지고 시민 조회에서는
		 * 항상 null 이다. 재배정·반려로 놓여 담당자가 없거나 공용 큐에 있으면 null 이며, 그때
		 * "누가 놓았는지"는 처리 이력 API 가 답한다. assignedControlCenter.name 은 관제센터
		 * 이름이지 담당자 이름이 아니므로 담당자 표시에 쓰면 안 된다.
		 */
		Long assignedOperatorId,
		String assignedOperatorName,
		Instant reportedAt,
		Instant createdAt,
		Instant updatedAt,
		List<ReportImageView> images,
		/**
		 * 조회한 담당자 본인의 최신 배정 상태. 재배정으로 넘긴 제보는 내 배정만 CANCELLED 되고
		 * 제보는 다른 담당자에게서 계속 진행되므로, 제보 상태만으로는 내 처리 결과를 알 수 없다.
		 * 시민 조회와 SUPER_ADMIN 조회에서는 null 이다.
		 */
		ReportOperatorAssignmentStatus myAssignmentStatus,
		/**
		 * LIVE 제보의 원본 녹화 상태. 라이브가 아니거나 녹화가 없으면 null이다.
		 * 담당자 조회에서만 채워지고 시민 조회에서는 항상 null이다.
		 */
		StreamRecordingStatus recordingStatus,
		String originalVideoUrl,
		Instant originalVideoUrlExpiresAt,
		String videoMimeType
) {
}
