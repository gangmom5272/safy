package com.ojimasam.safy.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.domain.auth.security.CurrentUser;
import com.ojimasam.safy.domain.user.dto.UpdateDisplayNameRequest;
import com.ojimasam.safy.domain.user.dto.UserResponse;
import com.ojimasam.safy.domain.user.service.UserService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "회원", description = "현재 로그인한 회원의 정보 관리")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
	private final UserService userService;
	public UserController(UserService userService) { this.userService = userService; }

	@GetMapping
	@Operation(summary = "내 회원 정보 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 정보 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.success(userService.me(CurrentUser.id(jwt)));
	}

	@PatchMapping
	@Operation(summary = "내 표시 이름 수정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "표시 이름 수정 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<UserResponse> update(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateDisplayNameRequest request) {
		return ApiResponse.success(userService.updateName(CurrentUser.id(jwt), request.displayName()));
	}

	@DeleteMapping
	@PreAuthorize("hasRole('CITIZEN')")
	@Operation(summary = "회원 탈퇴", description = "시민 계정을 탈퇴 처리하고 인증 토큰을 무효화합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "시민 권한 필요", content = @Content)
	public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Jwt jwt) {
		userService.withdraw(CurrentUser.id(jwt));
		return ResponseEntity.ok(ApiResponse.successWithoutData());
	}
}
