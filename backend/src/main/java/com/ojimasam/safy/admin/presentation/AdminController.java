package com.ojimasam.safy.admin.presentation;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.admin.presentation.ChangePasswordRequest;
import com.ojimasam.safy.admin.application.CreateOperatorRequest;
import com.ojimasam.safy.admin.application.OperatorResponse;
import com.ojimasam.safy.admin.application.UpdateOperatorRequest;
import com.ojimasam.safy.admin.presentation.UpdateOperatorStatusRequest;
import com.ojimasam.safy.admin.application.AdminService;
import com.ojimasam.safy.auth.infrastructure.security.CurrentUser;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리자 인증", description = "관리자 비밀번호 및 운영자 계정 관리")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
	private final AdminService adminService;
	public AdminController(AdminService adminService) { this.adminService = adminService; }

	@PatchMapping("/me/password")
	@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "관리자 비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비밀번호 변경 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 또는 현재 비밀번호 확인 실패", content = @Content)
	public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ChangePasswordRequest request) {
		adminService.changePassword(CurrentUser.id(jwt), request.currentPassword(), request.newPassword());
		return ApiResponse.successWithoutData();
	}

	@PostMapping("/operators")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관리자 생성", description = "SUPER_ADMIN이 OPERATOR 계정과 관제센터 소속을 생성합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 생성 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 중복 아이디", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	public ApiResponse<OperatorResponse> create(@Valid @RequestBody CreateOperatorRequest request) {
		return ApiResponse.success(adminService.create(request));
	}

	@GetMapping("/operators")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관리자 목록 조회", description = "page, size, sort 조건으로 관리자 목록을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	public ApiResponse<PageResponse<OperatorResponse>> list(Pageable pageable) {
		return ApiResponse.success(adminService.list(pageable));
	}

	@GetMapping("/operators/{operatorId}")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관리자 상세 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자를 찾을 수 없음", content = @Content)
	public ApiResponse<OperatorResponse> get(@PathVariable Long operatorId) {
		return ApiResponse.success(adminService.get(operatorId));
	}

	@GetMapping("/control-centers/{controlCenterId}/operators")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관제센터 소속 관리자 목록 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관제센터 소속 관리자 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터를 찾을 수 없음", content = @Content)
	public ApiResponse<PageResponse<OperatorResponse>> listByControlCenter(
			@PathVariable Long controlCenterId,
			Pageable pageable
	) {
		return ApiResponse.success(adminService.listByControlCenter(controlCenterId, pageable));
	}

	@PatchMapping("/operators/{operatorId}")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관리자 정보·소속 수정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 정보 수정 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자 또는 관제센터를 찾을 수 없음", content = @Content)
	public ApiResponse<OperatorResponse> update(@PathVariable Long operatorId,
			@Valid @RequestBody UpdateOperatorRequest request) {
		return ApiResponse.success(adminService.update(operatorId, request));
	}

	@PatchMapping("/operators/{operatorId}/status")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "관리자 정지·활성화", description = "관리자의 상태를 ACTIVE 또는 SUSPENDED로 변경합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관리자 상태 변경 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "허용되지 않는 상태값", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관리자를 찾을 수 없음", content = @Content)
	public ApiResponse<OperatorResponse> status(@PathVariable Long operatorId,
			@Valid @RequestBody UpdateOperatorStatusRequest request) {
		return ApiResponse.success(adminService.changeStatus(operatorId, request.status()));
	}
}
