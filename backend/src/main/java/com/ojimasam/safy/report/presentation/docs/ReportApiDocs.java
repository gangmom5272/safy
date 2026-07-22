package com.ojimasam.safy.report.presentation.docs;

import java.security.Principal;

import org.springframework.http.ResponseEntity;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.report.application.ReportDetailView;
import com.ojimasam.safy.report.application.ReportListItemView;
import com.ojimasam.safy.report.presentation.CreateReportRequest;
import com.ojimasam.safy.report.presentation.CreateReportResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;

public interface ReportApiDocs {
	String LOCAL_USER_HEADER_DESCRIPTION = "local 프로필에서 JWT 인증을 대신하는 테스트용 사용자 ID";

	@Operation(summary = "제보 취소", description = "접수 상태인 본인의 제보를 취소합니다.")
	@Parameter(name = "X-User-Id", in = ParameterIn.HEADER, description = LOCAL_USER_HEADER_DESCRIPTION)
	ResponseEntity<Void> cancel(Principal principal, Long reporterId, Long reportId);

	@Operation(summary = "내 제보 상세 조회")
	@Parameter(name = "X-User-Id", in = ParameterIn.HEADER, description = LOCAL_USER_HEADER_DESCRIPTION)
	ResponseEntity<ApiResponse<ReportDetailView>> getMine(Principal principal, Long reporterId, Long reportId);

	@Operation(summary = "내 제보 목록 조회")
	@Parameter(name = "X-User-Id", in = ParameterIn.HEADER, description = LOCAL_USER_HEADER_DESCRIPTION)
	ResponseEntity<ApiResponse<PageResponse<ReportListItemView>>> getMyList(Principal principal, Long reporterId,
			int page, int size);

	@Operation(summary = "제보 등록")
	@Parameter(name = "X-User-Id", in = ParameterIn.HEADER, description = LOCAL_USER_HEADER_DESCRIPTION)
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			description = "제보 유형에 맞는 요청 본문을 선택합니다.",
			required = true,
			content = @Content(
					schema = @Schema(discriminatorProperty = "reportType", oneOf = {
							CreateTextReportRequest.class,
							CreateImageReportRequest.class
					}),
					examples = {
						@ExampleObject(name = "TEXT", summary = "텍스트 제보", value = ReportExamples.CREATE_TEXT),
						@ExampleObject(name = "IMAGE", summary = "이미지 제보", value = ReportExamples.CREATE_IMAGE)
					}
			)
	)
	ResponseEntity<ApiResponse<CreateReportResponse>> create(Principal principal, Long reporterId,
			CreateReportRequest request);
}
