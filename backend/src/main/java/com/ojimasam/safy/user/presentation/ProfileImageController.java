package com.ojimasam.safy.user.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.config.SwaggerConfig;
import com.ojimasam.safy.user.application.ProfileImageService;
import com.ojimasam.safy.user.presentation.dto.IssueProfileImageUploadRequest;
import com.ojimasam.safy.user.presentation.dto.IssueProfileImageUploadResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me/profile-image")
@Tag(name = "프로필 사진", description = "회원이 직접 등록하는 프로필 사진")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
public class ProfileImageController {
	private final ProfileImageService profileImageService;

	public ProfileImageController(ProfileImageService profileImageService) {
		this.profileImageService = profileImageService;
	}

	@PostMapping("/presigned-upload")
	@Operation(summary = "프로필 사진 업로드 URL 발급",
			description = "MinIO에 직접 업로드할 presigned PUT URL과 필요한 헤더·만료 시각을 발급합니다. "
					+ "업로드 후 완료 처리를 호출해야 실제로 반영됩니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드 URL 발급 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 이미지 형식", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<IssueProfileImageUploadResponse> issueUpload(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody IssueProfileImageUploadRequest request) {
		return ApiResponse.success(IssueProfileImageUploadResponse.from(
				profileImageService.issueUpload(CurrentUser.id(jwt), request.contentType())));
	}

	@PostMapping("/complete")
	@Operation(summary = "프로필 사진 업로드 완료 처리",
			description = "업로드된 파일의 크기와 실제 형식을 검증하고 프로필 사진으로 반영합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "완료 처리 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ResponseEntity<Void> completeUpload(@AuthenticationPrincipal Jwt jwt) {
		profileImageService.completeUpload(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	@Operation(summary = "프로필 사진 초기화",
			description = "직접 등록한 프로필 사진을 삭제합니다. 이후에는 소셜 로그인 사진이 표시됩니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ResponseEntity<Void> deleteUploaded(@AuthenticationPrincipal Jwt jwt) {
		profileImageService.deleteUploadedImage(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}
}
