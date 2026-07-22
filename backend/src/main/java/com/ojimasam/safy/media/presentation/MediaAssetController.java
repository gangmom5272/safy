package com.ojimasam.safy.media.presentation;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.media.application.IssueImageUploadCommand;
import com.ojimasam.safy.media.application.IssueImageUploadService;
import com.ojimasam.safy.media.application.IssuedImageUpload;
import com.ojimasam.safy.report.presentation.ReportController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/media-assets")
public class MediaAssetController {
	private final IssueImageUploadService issueImageUploadService;

	public MediaAssetController(IssueImageUploadService issueImageUploadService) {
		this.issueImageUploadService = issueImageUploadService;
	}

	@PostMapping("/presigned-upload")
	@Parameter(name = "X-User-Id", in = ParameterIn.HEADER,
			description = "local 프로필에서 JWT 인증을 대신하는 테스트용 사용자 ID")
	public ResponseEntity<ApiResponse<IssueImageUploadResponse>> issueUpload(
			Principal principal,
			@RequestAttribute(value = ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, required = false) Long localUserId,
			@Valid @RequestBody IssueImageUploadRequest request
	) {
		Long userId = resolveAuthenticatedUserId(principal, localUserId);
		try {
			IssuedImageUpload upload = issueImageUploadService.issue(new IssueImageUploadCommand(
					userId, request.filename(), request.contentType()
			));
			return ResponseEntity.ok(ApiResponse.success(IssueImageUploadResponse.from(upload)));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		} catch (IllegalStateException exception) {
			throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, exception.getMessage());
		}
	}

	private Long resolveAuthenticatedUserId(Principal principal, Long localUserId) {
		if (principal == null) {
			if (localUserId == null) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
			return localUserId;
		}
		try {
			return Long.valueOf(principal.getName());
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
	}
}
