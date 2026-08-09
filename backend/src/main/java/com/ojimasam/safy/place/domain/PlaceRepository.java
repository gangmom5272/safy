package com.ojimasam.safy.place.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface PlaceRepository {
	Optional<Place> findByExternalPlaceId(String externalPlaceId);

	// JpaRepository 가 이미 제공하는 findById(Long) 와 이름이 겹치면 JpaPlaceRepository
	// 참조 시 어느 선언을 쓸지 컴파일러가 판단하지 못해 모호해진다(ReportRepository 가
	// findOneById 를 쓰는 것과 같은 이유).
	Optional<Place> findOneById(Long id);

	List<Place> findAllByActiveTrueOrderByNameAsc();

	/** 지역명·장소명 검색용. 활성 장소만, 이름 오름차순. */
	List<Place> findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(String name, Pageable pageable);

	Place save(Place place);
}
