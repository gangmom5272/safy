package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;

@Service
@Transactional(readOnly = true)
public class LocationQueryService {
	private final AdministrativeAreaResolver resolver;
	private final AddressResolver addressResolver;
	private final AdministrativeAreaRepository administrativeAreaRepository;

	public LocationQueryService(
			AdministrativeAreaResolver resolver,
			AddressResolver addressResolver,
			AdministrativeAreaRepository administrativeAreaRepository
	) {
		this.resolver = resolver;
		this.addressResolver = addressResolver;
		this.administrativeAreaRepository = administrativeAreaRepository;
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

	public List<LocationSearchResponse> search(String query, int limit) {
		if (query == null || query.isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "검색어는 필수입니다.");
		}
		if (limit < 1 || limit > 50) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "limit은 1 이상 50 이하여야 합니다.");
		}
		return administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
						query.strip(),
						AdministrativeAreaStatus.ACTIVE,
						PageRequest.of(0, limit)
				)
				.stream()
				.map(LocationSearchResponse::from)
				.toList();
	}
}
