package com.ojimasam.safy.publicdata.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CulturalEventRepository {
	Optional<CulturalEvent> findBySourceAndExternalId(String source, String externalId);

	List<CulturalEvent> findAllByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
			LocalDate onOrBefore,
			LocalDate onOrAfter
	);

	CulturalEvent save(CulturalEvent culturalEvent);
}
