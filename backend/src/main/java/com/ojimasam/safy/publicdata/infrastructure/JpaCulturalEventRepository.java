package com.ojimasam.safy.publicdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.publicdata.domain.CulturalEvent;
import com.ojimasam.safy.publicdata.domain.CulturalEventRepository;

public interface JpaCulturalEventRepository
		extends JpaRepository<CulturalEvent, Long>, CulturalEventRepository {
}
