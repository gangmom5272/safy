package com.ojimasam.safy.place.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.PlaceCategory;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaPlaceRepositoryTest {
	@Autowired
	private JpaPlaceRepository placeRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void searchesActivePlacesByNameCaseInsensitively() {
		place("역삼역", true);
		place("강남역", true);
		Place inactive = place("역삼동", false);
		entityManager.flush();

		var result = placeRepository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(
				"역삼", PageRequest.of(0, 10));

		assertThat(result).extracting(Place::getName).containsExactly("역삼역");
		assertThat(result).doesNotContain(inactive);
	}

	@Test
	void findOneByIdReturnsThePlace() {
		Place saved = place("역삼역", true);
		entityManager.flush();

		assertThat(placeRepository.findOneById(saved.getId())).isPresent();
	}

	private Place place(String name, boolean active) {
		Place place = Place.create("POI" + name.hashCode(), name, PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.5"), new BigDecimal("127.03"), "seoul-citydata");
		if (!active) {
			ReflectionTestUtils.setField(place, "active", false);
		}
		return entityManager.persist(place);
	}
}
