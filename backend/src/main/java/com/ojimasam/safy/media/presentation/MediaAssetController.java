package com.ojimasam.safy.media.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.media.application.IssueImageUploadCommand;
import com.ojimasam.safy.media.application.IssueImageUploadService;
import com.ojimasam.safy.media.application.IssuedImageUpload;
import com.ojimasam.safy.media.application.CompleteImageUploadCommand;
import com.ojimasam.safy.media.application.CompleteImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/reports/{reportId}/images")
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@Tag(name = "제보 이미지 업로드", description = "이미지 제보용 presigned URL 발급 및 업로드 완료 처리")
public class MediaAssetController {
	private final IssueImageUploadService issueImageUploadService;
	private final CompleteImageUploadService completeImageUploadService;

	public MediaAssetController(IssueImageUploadService issueImageUploadService,
			CompleteImageUploadService completeImageUploadService) {
		this.issueImageUploadService = issueImageUploadService;
		this.completeImageUploadService = completeImageUploadService;
	}

	@PostMapping("/presigned-upload")
	@Operation(summary = "이미지 업로드 URL 발급", description = "MinIO에 직접 업로드할 presigned PUT URL과 필요한 헤더·만료 시각을 발급합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드 URL 발급 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일명·형식 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "업로드 발급 요청 한도 초과", content = @Content)
	public ResponseEntity<ApiResponse<IssueImageUploadResponse>> issueUpload(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId,
			@Valid @RequestBody IssueImageUploadRequest request
	) {
		Long userId = CurrentUser.id(jwt);
		try {
			IssuedImageUpload upload = issueImageUploadService.issue(new IssueImageUploadCommand(
					userId, reportId, request.filename(), request.contentType()
			));
			return ResponseEntity.ok(ApiResponse.success(IssueImageUploadResponse.from(upload)));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, exception.getMessage());
		}
	}

	@PostMapping("/{mediaAssetId}/complete")
	@Operation(summary = "이미지 업로드 완료 처리", description = "MinIO 업로드 완료 후 실제 파일 존재·크기·형식을 검증하고 미디어를 사용 가능 상태로 전환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "업로드 완료 처리 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "미디어를 찾을 수 없음", content = @Content)
	public ResponseEntity<Void> completeUpload(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId,
			@PathVariable Long mediaAssetId
	) {
		completeImageUploadService.complete(new CompleteImageUploadCommand(
				CurrentUser.id(jwt), reportId, mediaAssetId
		));
		return ResponseEntity.noContent().build();
	}

}
