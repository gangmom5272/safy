package com.ojimasam.safy.publicdata.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.publicdata.application.PublicDataQueryService;
import com.ojimasam.safy.publicdata.application.dto.CulturalEventView;
import com.ojimasam.safy.publicdata.application.dto.PlaceCongestionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 공개 도시데이터이고 로그인 전 지도에서 조회하므로 인증 없이 열려 있다. */
@RestController
@RequestMapping("/api/v1/public-data")
@Tag(name = "공공데이터", description = "지도에 표시할 인구 밀집·문화 행사 조회 (인증 불필요)")
public class PublicDataController {
	private final PublicDataQueryService publicDataQueryService;

	public PublicDataController(PublicDataQueryService publicDataQueryService) {
		this.publicDataQueryService = publicDataQueryService;
	}

	@GetMapping("/congestion")
	@Operation(
			summary = "지역별 최신 인구 밀집 조회",
			description = "서울시 실시간 도시데이터 기준으로 활성 지역의 가장 최근 관측값을 반환합니다."
	)
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	public ApiResponse<List<PlaceCongestionResponse>> congestion() {
		return ApiResponse.success(publicDataQueryService.latestCongestion());
	}

	@GetMapping("/events")
	@Operation(summary = "진행 중인 문화 행사 조회", description = "오늘 날짜를 기준으로 진행 중인 행사만 반환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	public ApiResponse<List<CulturalEventView>> events() {
		return ApiResponse.success(publicDataQueryService.ongoingEvents());
	}
}
