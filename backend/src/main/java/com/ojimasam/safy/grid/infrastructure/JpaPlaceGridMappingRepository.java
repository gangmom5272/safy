package com.ojimasam.safy.grid.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.grid.domain.PlaceGridMapping;
import com.ojimasam.safy.grid.domain.PlaceGridMappingRepository;

public interface JpaPlaceGridMappingRepository
		extends JpaRepository<PlaceGridMapping, Long>, PlaceGridMappingRepository {
}