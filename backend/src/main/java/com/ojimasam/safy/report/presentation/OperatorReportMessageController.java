package com.ojimasam.safy.report.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.report.application.ReportMessageResponse;
import com.ojimasam.safy.report.application.ReportMessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/operator/reports/{reportId}/messages")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "제보 메시지(담당자)", description = "담당자가 시민과 주고받는 제보 메시지")
public class OperatorReportMessageController {
	private final ReportMessageService service;

	public OperatorReportMessageController(ReportMessageService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(summary = "제보 메시지 전송(담당자)", description = "담당자가 처리 중인 제보 스레드에 메시지를 전송합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 전송 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "메시지 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<ReportMessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long reportId,
			@Valid @RequestBody SendReportMessageRequest request) {
		return ApiResponse.success(service.sendFromOperator(
				CurrentUser.id(jwt), reportId, request.content()));
	}

	@GetMapping
	@Operation(summary = "제보 메시지 목록 조회(담당자)", description = "담당자가 제보 스레드의 메시지를 페이지 단위로 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "제보를 찾을 수 없음", content = @Content)
	public ApiResponse<PageResponse<ReportMessageResponse>> list(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId, Pageable pageable) {
		return ApiResponse.success(PageResponse.from(
				service.listForOperator(CurrentUser.id(jwt), reportId, pageable)));
	}
}
