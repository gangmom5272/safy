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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/media-assets")
@PreAuthorize("hasRole('CITIZEN')")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class MediaAssetController {
	private final IssueImageUploadService issueImageUploadService;

	public MediaAssetController(IssueImageUploadService issueImageUploadService) {
		this.issueImageUploadService = issueImageUploadService;
	}

	@PostMapping("/presigned-upload")
	public ResponseEntity<ApiResponse<IssueImageUploadResponse>> issueUpload(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody IssueImageUploadRequest request
	) {
		Long userId = CurrentUser.id(jwt);
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

}
