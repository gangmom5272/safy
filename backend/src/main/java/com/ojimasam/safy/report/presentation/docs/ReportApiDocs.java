package com.ojimasam.safy.report.presentation.docs;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.report.application.ReportDetailView;
import com.ojimasam.safy.report.application.ReportListItemView;
import com.ojimasam.safy.report.application.ReportStatusHistoryResponse;
import com.ojimasam.safy.report.presentation.CreateReportRequest;
import com.ojimasam.safy.report.presentation.CreateReportResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "bearerAuth")
public interface ReportApiDocs {
	@Operation(summary = "제보 취소", description = "접수 상태인 본인의 제보를 취소합니다.")
	ResponseEntity<Void> cancel(Jwt jwt, Long reportId);

	@Operation(summary = "내 제보 상세 조회")
	ResponseEntity<ApiResponse<ReportDetailView>> getMine(Jwt jwt, Long reportId);

	@Operation(summary = "내 제보 상태 변경 이력 조회")
	ResponseEntity<ApiResponse<java.util.List<ReportStatusHistoryResponse>>> getMyStatusHistory(
			Jwt jwt,
			Long reportId
	);

	@Operation(summary = "내 제보 목록 조회")
	ResponseEntity<ApiResponse<PageResponse<ReportListItemView>>> getMyList(Jwt jwt,
			int page, int size);

	@Operation(summary = "제보 등록")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "제보 유형에 맞는 요청 본문을 선택합니다.",
			required = true,
			content = @Content(
					schema = @Schema(implementation = CreateReportRequest.class),
					examples = {
						@ExampleObject(name = "IMAGE", summary = "이미지 제보 접수", value = ReportExamples.CREATE_IMAGE),
						@ExampleObject(name = "LIVE", summary = "라이브 제보 접수", value = ReportExamples.CREATE_LIVE)
					}
			)
	)
	ResponseEntity<ApiResponse<CreateReportResponse>> create(Jwt jwt,
			CreateReportRequest request);
}
