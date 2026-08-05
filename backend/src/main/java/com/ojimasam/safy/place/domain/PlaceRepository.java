package com.ojimasam.safy.place.domain;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository {
	Optional<Place> findByExternalPlaceId(String externalPlaceId);

	Optional<Place> findById(Long id);

	List<Place> findAllByActiveTrueOrderByNameAsc();

	Place save(Place place);
}
