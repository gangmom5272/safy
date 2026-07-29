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

	public LocationController(LocationQueryService locationQueryService) {
		this.locationQueryService = locationQueryService;
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
	@Operation(summary = "지역명 검색", description = "이름에 검색어가 포함된 활성 행정구역을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지역명 검색 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색어 또는 limit", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	public ApiResponse<List<LocationSearchResponse>> search(
			@RequestParam String query,
			@RequestParam(defaultValue = "10") int limit
	) {
		return ApiResponse.success(locationQueryService.search(query, limit));
	}
}
