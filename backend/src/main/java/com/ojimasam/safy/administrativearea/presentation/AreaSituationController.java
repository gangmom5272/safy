package com.ojimasam.safy.administrativearea.presentation;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.administrativearea.application.dto.AreaMapResponse;
import com.ojimasam.safy.administrativearea.application.AreaSituationQueryService;
import com.ojimasam.safy.administrativearea.application.dto.AreaStatusResponse;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.report.application.dto.ReportCursorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/areas")
@Tag(name = "지역 상황", description = "프론트 지도/상황판 결합용 지역 상황 조회")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
public class AreaSituationController {
	private final AreaSituationQueryService areaSituationQueryService;

	public AreaSituationController(AreaSituationQueryService areaSituationQueryService) {
		this.areaSituationQueryService = areaSituationQueryService;
	}

	@GetMapping("/{areaId}/status")
	@Operation(summary = "행정구역 상황 요약 조회", description = "행정구역의 위험도와 활성/라이브 제보 수를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역 상황 요약 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행정구역을 찾을 수 없음", content = @Content)
	public ApiResponse<AreaStatusResponse> status(@PathVariable Long areaId) {
		return ApiResponse.success(areaSituationQueryService.status(areaId));
	}

	@GetMapping("/{areaId}/map")
	@Operation(summary = "행정구역 지도 데이터 조회", description = "지역 상황 요약, 경계 GeoJSON, 라이브 제보 목록을 함께 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역 지도 데이터 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행정구역을 찾을 수 없음", content = @Content)
	public ApiResponse<AreaMapResponse> map(
			@PathVariable Long areaId,
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(areaSituationQueryService.map(areaId, cursor, size));
	}

	@GetMapping("/{areaId}/reports/live")
	@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
	@Operation(summary = "행정구역별 라이브 제보 목록 조회",
			description = "해당 행정구역의 라이브 제보를 커서 기반으로 조회합니다. 라이브 세션 정보를 담으므로 관제 전용입니다. "
					+ "시민 화면은 GET /api/v1/feed/areas/{areaId}/reports 를 사용합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역별 라이브 제보 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "담당자 권한 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "행정구역을 찾을 수 없음", content = @Content)
	public ApiResponse<ReportCursorResponse> liveReports(
			@PathVariable Long areaId,
			@RequestParam(required = false) Instant cursor,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(areaSituationQueryService.liveReports(areaId, cursor, size));
	}
}
