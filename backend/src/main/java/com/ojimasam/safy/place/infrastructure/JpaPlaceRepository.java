package com.ojimasam.safy.place.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;

public interface JpaPlaceRepository extends JpaRepository<Place, Long>, PlaceRepository {
}
