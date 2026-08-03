package com.ojimasam.safy.grid.presentation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.grid.application.GridRiskQueryService;
import com.ojimasam.safy.grid.application.dto.GridRiskResponse;
import com.ojimasam.safy.grid.application.dto.GridRiskTrendPoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 지도에 표시할 격자 위험도. 공개 지도에서 로그인 전에도 조회하므로 인증 없이 열려 있다.
 * 제보 내용이나 위치는 나가지 않고 격자 단위로 집계된 점수만 나간다.
 */
@RestController
@RequestMapping("/api/v1/grid-risks")
@Tag(name = "격자 위험도", description = "100m 격자 단위 위험도 조회 (인증 불필요)")
public class GridRiskController {
	/**
	 * 격자 위험도는 스케줄러가 3분마다 다시 계산한다. 그보다 짧게 캐시해도 새 값이 없다.
	 * 지도를 확대·축소하며 같은 범위를 오갈 때 네트워크를 타지 않게 한다.
	 */
	private static final Duration CACHE_TTL = Duration.ofMinutes(3);

	/** 기본 6시간. 이력이 값 변화 시점에만 쌓여 점 개수는 이 창보다 훨씬 적다. */
	private static final Duration DEFAULT_TREND_LOOKBACK = Duration.ofHours(6);

	private final GridRiskQueryService gridRiskQueryService;

	public GridRiskController(GridRiskQueryService gridRiskQueryService) {
		this.gridRiskQueryService = gridRiskQueryService;
	}

	@GetMapping
	@Operation(
			summary = "뷰포트 안 격자 위험도 조회",
			description = """
					지도에 보이는 범위의 격자 위험도를 반환합니다.

					근거가 없는 격자는 응답에 포함되지 않습니다. 화면에서 색칠하지 말아야 하며,
					위험도 0(안전함)으로 표시하면 안 됩니다.

					**resolution** 으로 묶는 배수를 지정합니다. 줌 아웃 시 큰 값을 보내면
					서버가 100m 계산 결과를 최댓값으로 묶어 주므로 응답과 렌더링이 함께
					가벼워집니다(레벨 7이면 8 정도, 최대 16). 클라이언트에서 다시 묶지
					마십시오 — 큰 칸에서 재계산하면 위험 지점이 칸 중심에서 밀려나
					위험도가 사라져 보입니다.

					응답에는 그릴 사각형의 경계와 score 가 함께 담깁니다. 등급만으로는
					'약간 붐빔'(45)과 '붐빔'(60)이 둘 다 medium 이라 구분되지 않으니,
					등급으로 색을 정하고 점수로 농도를 나누십시오.

					뷰포트는 격자 경계로 스냅해서 조회하므로 같은 칸 범위를 보는 동안
					응답이 같습니다. 3분간 캐시됩니다.
					"""
	)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	public ResponseEntity<ApiResponse<List<GridRiskResponse>>> risks(
			@Parameter(description = "남쪽 경계 위도", example = "37.55")
			@RequestParam BigDecimal minLat,
			@Parameter(description = "북쪽 경계 위도", example = "37.57")
			@RequestParam BigDecimal maxLat,
			@Parameter(description = "서쪽 경계 경도", example = "126.92")
			@RequestParam BigDecimal minLng,
			@Parameter(description = "동쪽 경계 경도", example = "126.94")
			@RequestParam BigDecimal maxLng,
			@Parameter(description = "묶을 배수. 1이면 100m 그대로, 8이면 800m. 최대 16", example = "1")
			@RequestParam(required = false, defaultValue = "1") int resolution
	) {
		List<GridRiskResponse> risks =
				gridRiskQueryService.risksInBounds(minLat, maxLat, minLng, maxLng, resolution);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
				.body(ApiResponse.success(risks));
	}

	@GetMapping("/{gridCode}/trend")
	@Operation(
			summary = "격자 위험도 추이 조회",
			description = """
					한 격자의 위험도 변화를 최근 것부터 반환합니다.

					이력은 값이 바뀔 때만 쌓입니다. 점 사이 간격이 일정하지 않고, 다음 점이
					나올 때까지 그 값이 유지된 것으로 읽어야 합니다(계단 함수).
					"""
	)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	public ResponseEntity<ApiResponse<List<GridRiskTrendPoint>>> trend(
			@Parameter(description = "격자 셀 코드", example = "S100-r174-c199")
			@PathVariable String gridCode,
			@Parameter(description = "조회할 기간(시간). 기본 6시간", example = "6")
			@RequestParam(required = false) Integer hours
	) {
		Duration lookback = hours == null ? DEFAULT_TREND_LOOKBACK : Duration.ofHours(hours);

		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
				.body(ApiResponse.success(gridRiskQueryService.trend(gridCode, lookback)));
	}
}
