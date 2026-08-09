package com.ojimasam.safy.controlcenter.presentation;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.controlcenter.application.dto.AssignControlCenterAreaRequest;
import com.ojimasam.safy.controlcenter.application.dto.ControlCenterAreaResponse;
import com.ojimasam.safy.controlcenter.application.dto.ControlCenterResponse;
import com.ojimasam.safy.controlcenter.application.ControlCenterService;
import com.ojimasam.safy.controlcenter.application.dto.CreateControlCenterRequest;
import com.ojimasam.safy.controlcenter.application.dto.UpdateControlCenterAreaRequest;
import com.ojimasam.safy.controlcenter.application.dto.UpdateControlCenterRequest;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/control-centers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "관제센터", description = "관제센터 및 담당 행정구역 관리")
@SecurityRequirement(name = "bearerAuth")
public class ControlCenterController {
	private final ControlCenterService controlCenterService;

	public ControlCenterController(ControlCenterService controlCenterService) {
		this.controlCenterService = controlCenterService;
	}

	@PostMapping
	@Operation(summary = "관제센터 생성")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "관제센터 생성 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "관제센터 코드 중복", content = @Content)
	public ResponseEntity<ApiResponse<ControlCenterResponse>> create(
			@Valid @RequestBody CreateControlCenterRequest request
	) {
		ControlCenterResponse response = handleInvalidInput(() -> controlCenterService.create(request));
		return ResponseEntity.created(URI.create("/api/v1/admin/control-centers/" + response.id()))
				.body(ApiResponse.success(response));
	}

	@GetMapping
	@Operation(summary = "관제센터 목록 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관제센터 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	public ApiResponse<PageResponse<ControlCenterResponse>> list(
			@RequestParam(required = false) ControlCenterStatus status,
			Pageable pageable
	) {
		return ApiResponse.success(controlCenterService.list(status, pageable));
	}

	@GetMapping("/{centerId}")
	@Operation(summary = "관제센터 상세 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관제센터 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터를 찾을 수 없음", content = @Content)
	public ApiResponse<ControlCenterResponse> get(@PathVariable Long centerId) {
		return ApiResponse.success(controlCenterService.get(centerId));
	}

	@PatchMapping("/{centerId}")
	@Operation(summary = "관제센터 수정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관제센터 수정 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터를 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "비활성화 전환 시 근무 중인 담당자 존재", content = @Content)
	public ApiResponse<ControlCenterResponse> update(
			@PathVariable Long centerId,
			@Valid @RequestBody UpdateControlCenterRequest request
	) {
		return ApiResponse.success(handleInvalidInput(() -> controlCenterService.update(centerId, request)));
	}

	@DeleteMapping("/{centerId}")
	@Operation(summary = "관제센터 비활성화", description = "관제센터 이력을 보존하기 위해 물리 삭제하지 않고 비활성화합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "비활성화 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터를 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "근무 중인 담당자가 있어 비활성화할 수 없음", content = @Content)
	public ResponseEntity<Void> deactivate(@PathVariable Long centerId) {
		controlCenterService.deactivate(centerId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{centerId}/areas")
	@Operation(summary = "관제센터 담당 구역 목록 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "담당 구역 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터를 찾을 수 없음", content = @Content)
	public ApiResponse<List<ControlCenterAreaResponse>> listAreas(@PathVariable Long centerId) {
		return ApiResponse.success(controlCenterService.listAreas(centerId));
	}

	@PostMapping("/{centerId}/areas")
	@Operation(summary = "관제센터 담당 구역 설정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "담당 구역 설정 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관제센터 또는 행정구역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 같은 역할로 담당 중인 구역", content = @Content)
	public ResponseEntity<ApiResponse<ControlCenterAreaResponse>> assignArea(
			@PathVariable Long centerId,
			@Valid @RequestBody AssignControlCenterAreaRequest request
	) {
		ControlCenterAreaResponse response = handleInvalidInput(
				() -> controlCenterService.assignArea(centerId, request)
		);
		return ResponseEntity.created(URI.create(
				"/api/v1/admin/control-centers/" + centerId + "/areas/" + response.id()
		)).body(ApiResponse.success(response));
	}

	@PatchMapping("/{centerId}/areas/{coverageId}")
	@Operation(summary = "관제센터 담당 구역 수정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "담당 구역 수정 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "담당 구역을 찾을 수 없음", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 같은 역할로 담당 중인 구역", content = @Content)
	public ApiResponse<ControlCenterAreaResponse> updateArea(
			@PathVariable Long centerId,
			@PathVariable Long coverageId,
			@Valid @RequestBody UpdateControlCenterAreaRequest request
	) {
		return ApiResponse.success(handleInvalidInput(
				() -> controlCenterService.updateArea(centerId, coverageId, request)
		));
	}

	@DeleteMapping("/{centerId}/areas/{coverageId}")
	@Operation(summary = "관제센터 담당 구역 해제", description = "담당 구역 이력을 보존하기 위해 종료 시각과 비활성 상태를 기록합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "담당 구역 해제 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "SUPER_ADMIN 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "담당 구역을 찾을 수 없음", content = @Content)
	public ResponseEntity<Void> deactivateArea(
			@PathVariable Long centerId,
			@PathVariable Long coverageId
	) {
		controlCenterService.deactivateArea(centerId, coverageId);
		return ResponseEntity.noContent().build();
	}

	private <T> T handleInvalidInput(Supplier<T> action) {
		try {
			return action.get();
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, exception.getMessage());
		}
	}
}
