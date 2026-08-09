package com.ojimasam.safy.administrativearea.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaQueryService;
import com.ojimasam.safy.administrativearea.application.dto.AdministrativeAreaResponse;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaType;
import com.ojimasam.safy.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/administrative-areas")
@Tag(name = "행정구역", description = "활성 행정구역 조회")
@SecurityRequirement(name = "bearerAuth")
public class AdministrativeAreaController {
	private final AdministrativeAreaQueryService administrativeAreaQueryService;

	public AdministrativeAreaController(AdministrativeAreaQueryService administrativeAreaQueryService) {
		this.administrativeAreaQueryService = administrativeAreaQueryService;
	}

	@GetMapping
	@Operation(summary = "행정구역 목록 조회", description = "행정구역 유형과 상위 행정구역 코드로 활성 행정구역을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행정구역 목록 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<List<AdministrativeAreaResponse>> list(
			@RequestParam(defaultValue = "SIGUNGU") AdministrativeAreaType type,
			@RequestParam(required = false) String parentCode
	) {
		return ApiResponse.success(administrativeAreaQueryService.list(type, parentCode));
	}

	@GetMapping("/{areaId}")
	@Operation(summary = "행정구역 상세 조회")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "행정구역 상세 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행정구역을 찾을 수 없음", content = @Content)
	public ApiResponse<AdministrativeAreaResponse> get(@PathVariable Long areaId) {
		return ApiResponse.success(administrativeAreaQueryService.get(areaId));
	}
}
