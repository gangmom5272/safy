package com.ojimasam.safy.place.presentation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.place.application.PlaceSituationQueryService;
import com.ojimasam.safy.place.application.dto.PlaceStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "장소 상황", description = "서울시 121장소 검색용 상황 요약 조회")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('CITIZEN','OPERATOR','SUPER_ADMIN')")
public class PlaceSituationController {
	private final PlaceSituationQueryService placeSituationQueryService;

	public PlaceSituationController(PlaceSituationQueryService placeSituationQueryService) {
		this.placeSituationQueryService = placeSituationQueryService;
	}

	@GetMapping("/{placeId}/status")
	@Operation(summary = "장소 상황 요약 조회",
			description = "그 장소 자신의 혼잡도와 경계 안 최근 제보 수를 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 상황 요약 조회 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음", content = @Content)
	public ApiResponse<PlaceStatusResponse> status(@PathVariable Long placeId) {
		return ApiResponse.success(placeSituationQueryService.status(placeId));
	}
}
