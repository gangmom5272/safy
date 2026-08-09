package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.administrativearea.application.dto.LocationResolveResponse;
import com.ojimasam.safy.administrativearea.application.dto.LocationSearchResponse;
import com.ojimasam.safy.place.domain.PlaceRepository;

@Service
@Transactional(readOnly = true)
public class LocationQueryService {
	private final AdministrativeAreaResolver resolver;
	private final AddressResolver addressResolver;
	private final AdministrativeAreaRepository administrativeAreaRepository;
	private final PlaceRepository placeRepository;
	private final PlaceKeywordSearcher placeKeywordSearcher;

	public LocationQueryService(
			AdministrativeAreaResolver resolver,
			AddressResolver addressResolver,
			AdministrativeAreaRepository administrativeAreaRepository,
			PlaceRepository placeRepository,
			PlaceKeywordSearcher placeKeywordSearcher
	) {
		this.resolver = resolver;
		this.addressResolver = addressResolver;
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.placeRepository = placeRepository;
		this.placeKeywordSearcher = placeKeywordSearcher;
	}

	public LocationResolveResponse resolve(BigDecimal latitude, BigDecimal longitude) {
		String address = resolveAddressQuietly(latitude, longitude);
		try {
			return resolver.resolve(latitude, longitude)
					.flatMap(resolved -> administrativeAreaRepository.findByCodeAndStatus(
							resolved.code(),
							AdministrativeAreaStatus.ACTIVE
					))
					.map(area -> LocationResolveResponse.resolved(area, address, latitude, longitude))
					.orElseGet(() -> LocationResolveResponse.unresolved(
							address,
							latitude,
							longitude,
							"지원 행정구역을 찾을 수 없습니다."
					));
		} catch (AdministrativeAreaResolutionException exception) {
			return LocationResolveResponse.unresolved(address, latitude, longitude, exception.getMessage());
		}
	}

	/** 주소는 부가 정보라, 조회에 실패해도 행정구역 판별 결과는 그대로 내려준다. */
	private String resolveAddressQuietly(BigDecimal latitude, BigDecimal longitude) {
		try {
			return addressResolver.resolve(latitude, longitude).orElse(null);
		} catch (AddressResolutionException exception) {
			return null;
		}
	}

	/**
	 * 자치구 → 서울시 121장소 → 외부 키워드 검색 순으로 찾아 한 목록에 합친다.
	 *
	 * <p>순서에 이유가 있다. 121장소를 외부 검색보다 앞에 두는 것은 그쪽이 <b>실시간 혼잡도를
	 * 가진 장소</b>라 사용자에게 더 쓸모 있기 때문이다("역삼역"은 121장소로 잡히고 외부 검색까지
	 * 갈 필요가 없다). 외부 검색은 우리 데이터로 찾을 수 없는 구체적 장소("멀티캠퍼스 역삼지점")
	 * 만 담당한다.
	 *
	 * <p>앞 단계가 limit 을 채우면 뒤 단계는 조회하지 않는다 — 외부 호출을 아끼는 효과도 있다.
	 */
	public List<LocationSearchResponse> search(String query, int limit) {
		if (query == null || query.isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "검색어는 필수입니다.");
		}
		if (limit < 1 || limit > 50) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "limit은 1 이상 50 이하여야 합니다.");
		}

		String trimmed = query.strip();

		List<LocationSearchResponse> areas = administrativeAreaRepository
				.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
						trimmed, AdministrativeAreaStatus.ACTIVE, PageRequest.of(0, limit))
				.stream()
				.map(LocationSearchResponse::fromArea)
				.toList();

		int afterAreas = limit - areas.size();
		if (afterAreas <= 0) {
			return areas;
		}

		List<LocationSearchResponse> places = placeRepository
				.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(trimmed, PageRequest.of(0, afterAreas))
				.stream()
				.map(LocationSearchResponse::fromPlace)
				.toList();

		int afterPlaces = afterAreas - places.size();
		if (afterPlaces <= 0) {
			return Stream.concat(areas.stream(), places.stream()).toList();
		}

		List<LocationSearchResponse> keywordPlaces = placeKeywordSearcher.search(trimmed, afterPlaces)
				.stream()
				.map(LocationSearchResponse::fromKeywordPlace)
				.toList();

		return Stream.of(areas, places, keywordPlaces).flatMap(List::stream).toList();
	}
}
