package com.ojimasam.safy.administrativearea.presentation;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.administrativearea.application.LocationQueryService;
import com.ojimasam.safy.administrativearea.application.dto.LocationResolveResponse;
import com.ojimasam.safy.administrativearea.application.dto.LocationSearchResponse;
import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.place.application.CoordinateSituationQueryService;
import com.ojimasam.safy.place.application.dto.CoordinateStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "위치", description = "프론트 지도 결합용 위치 조회")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
public class LocationController {
	private final LocationQueryService locationQueryService;
	private final CoordinateSituationQueryService coordinateSituationQueryService;

	public LocationController(
			LocationQueryService locationQueryService,
			CoordinateSituationQueryService coordinateSituationQueryService
	) {
		this.locationQueryService = locationQueryService;
		this.coordinateSituationQueryService = coordinateSituationQueryService;
	}

	@GetMapping("/resolve")
	@Operation(summary = "좌표 기반 행정구역 해석", description = "위경도를 활성 행정구역으로 해석합니다. 해석 실패 시에도 200과 resolved=false를 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "위치 해석 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<LocationResolveResponse> resolve(
			@RequestParam BigDecimal latitude,
			@RequestParam BigDecimal longitude
	) {
		return ApiResponse.success(locationQueryService.resolve(latitude, longitude));
	}

	@GetMapping("/search")
	@Operation(summary = "지역명·장소명 통합 검색",
			description = """
					자치구, 서울시 121장소, 외부 키워드 검색 결과를 한 목록으로 조회합니다.

					`type` 으로 상황 요약을 어디에 물을지 정합니다.
					- `AREA`: `/areas/{id}/status`
					- `PLACE`: `/places/{id}/status`
					- `KEYWORD`: `/locations/status?latitude=&longitude=` (우리 식별자가 없어 좌표로 조회)
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색어 또는 limit", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<List<LocationSearchResponse>> search(
			@RequestParam String query,
			@RequestParam(defaultValue = "10") int limit
	) {
		return ApiResponse.success(locationQueryService.search(query, limit));
	}

	@GetMapping("/status")
	@Operation(summary = "좌표 상황 요약 조회",
			description = """
					임의 좌표의 상황 요약입니다. 외부 검색으로 찾은 구체적 장소에 씁니다.

					혼잡도는 좌표가 속한 격자를 덮는 121장소(없으면 최근접 2km 안)의 실제 관측이고,
					최근 제보는 좌표 중심 반경 안의 30분 이내 공개 제보 수입니다. 두 범위가 다르므로
					`congestionMatchType` 과 `reportRadiusMeters` 를 화면 각주에 밝혀야 합니다.

					근거가 없으면 `congestion` 이 null 입니다 — 121장소가 근처에 없는 좌표가 실제로 많습니다.
					""")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌표 상황 요약 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "좌표 누락", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<CoordinateStatusResponse> status(
			@RequestParam BigDecimal latitude,
			@RequestParam BigDecimal longitude
	) {
		return ApiResponse.success(coordinateSituationQueryService.status(latitude, longitude));
	}
}
