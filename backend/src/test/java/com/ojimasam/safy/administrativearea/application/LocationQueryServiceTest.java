package com.ojimasam.safy.administrativearea.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ojimasam.safy.administrativearea.application.dto.LocationSearchResponse;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.type.AdministrativeAreaStatus;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.PlaceRepository;
import com.ojimasam.safy.place.domain.type.PlaceCategory;

@ExtendWith(MockitoExtension.class)
class LocationQueryServiceTest {
	@Mock AdministrativeAreaResolver resolver;
	@Mock AddressResolver addressResolver;
	@Mock AdministrativeAreaRepository administrativeAreaRepository;
	@Mock PlaceRepository placeRepository;
	@Mock PlaceKeywordSearcher placeKeywordSearcher;

	/** 자치구가 아닌 이름(역삼역 등)도 검색되려면 121장소가 같은 목록에 섞여야 한다. */
	@Test
	void mergesAreasAndPlacesWithAreasFirst() {
		// area()/place() 는 각각 새 mock 을 만들며 그 안에서 when(...).thenReturn(...) 을
		// 완결한다. 바깥쪽 when(...).thenReturn(List.of(area(...))) 처럼 진행 중인 스터빙의
		// 인자 자리에서 호출하면 Mockito 가 두 스터빙을 뒤섞어 "unfinished stubbing" 으로
		// 오인한다. 그래서 미리 변수로 뽑아 둔다.
		AdministrativeArea gangnam = area(1L, "강남구");
		Place yeoksam = place(14L, "역삼역");
		when(administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
				eq("역삼"), eq(AdministrativeAreaStatus.ACTIVE), any(Pageable.class)))
				.thenReturn(List.of(gangnam));
		when(placeRepository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(
				eq("역삼"), any(Pageable.class)))
				.thenReturn(List.of(yeoksam));

		List<LocationSearchResponse> result = service().search("역삼", 10);

		assertThat(result).extracting(LocationSearchResponse::type)
				.containsExactly(LocationSearchResponse.TYPE_AREA, LocationSearchResponse.TYPE_PLACE);
		assertThat(result.get(1).id()).isEqualTo(14L);
		assertThat(result.get(1).latitude()).isNotNull();
	}

	/** limit 을 자치구가 이미 다 채우면 장소는 조회하지 않는다. */
	@Test
	void skipsPlaceQueryWhenAreasAlreadyFillTheLimit() {
		AdministrativeArea gangnam = area(1L, "강남구");
		when(administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
				eq("강남"), eq(AdministrativeAreaStatus.ACTIVE), eq(PageRequest.of(0, 1))))
				.thenReturn(List.of(gangnam));

		List<LocationSearchResponse> result = service().search("강남", 1);

		assertThat(result).hasSize(1);
		verifyNoInteractions(placeRepository);
	}

	/** 자치구가 하나도 없으면 남는 자리 전부를 장소로 채운다. */
	@Test
	void fillsRemainingSlotsWithPlacesWhenNoAreaMatches() {
		Place yeoksam = place(14L, "역삼역");
		when(administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
				eq("역삼역"), eq(AdministrativeAreaStatus.ACTIVE), any(Pageable.class)))
				.thenReturn(List.of());
		when(placeRepository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(
				eq("역삼역"), eq(PageRequest.of(0, 10))))
				.thenReturn(List.of(yeoksam));

		List<LocationSearchResponse> result = service().search("역삼역", 10);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).type()).isEqualTo(LocationSearchResponse.TYPE_PLACE);
	}

	/**
	 * 우리 데이터로 찾을 수 없는 구체적 장소는 외부 검색이 담당한다. 121장소를 외부보다
	 * 앞에 두는 것은 그쪽이 실시간 혼잡도를 가진 장소라 더 쓸모 있기 때문이다.
	 */
	@Test
	void appendsKeywordResultsAfterAreasAndPlaces() {
		Place yeoksam = place(14L, "역삼역");
		KeywordPlace multicampus = new KeywordPlace(
				"멀티캠퍼스 역삼지점", "서울 강남구 테헤란로 212",
				new BigDecimal("37.5013"), new BigDecimal("127.0396"));
		when(administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
				eq("역삼"), eq(AdministrativeAreaStatus.ACTIVE), any(Pageable.class)))
				.thenReturn(List.of());
		when(placeRepository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(
				eq("역삼"), any(Pageable.class)))
				.thenReturn(List.of(yeoksam));
		when(placeKeywordSearcher.search("역삼", 9)).thenReturn(List.of(multicampus));

		List<LocationSearchResponse> result = service().search("역삼", 10);

		assertThat(result).extracting(LocationSearchResponse::type)
				.containsExactly(LocationSearchResponse.TYPE_PLACE, LocationSearchResponse.TYPE_KEYWORD);
		LocationSearchResponse keyword = result.get(1);
		// 외부 검색 결과는 우리 DB에 없어 식별자가 없다. 좌표가 유일한 기준이다.
		assertThat(keyword.id()).isNull();
		assertThat(keyword.name()).isEqualTo("멀티캠퍼스 역삼지점");
		assertThat(keyword.displayName()).isEqualTo("서울 강남구 테헤란로 212");
		assertThat(keyword.latitude()).isEqualByComparingTo("37.5013");
	}

	/** 앞 단계가 limit 을 채우면 외부 호출을 하지 않는다. */
	@Test
	void skipsKeywordSearchWhenEarlierTiersFillTheLimit() {
		Place yeoksam = place(14L, "역삼역");
		when(administrativeAreaRepository.findByNameContainingIgnoreCaseAndStatusOrderByNameAsc(
				eq("역삼역"), eq(AdministrativeAreaStatus.ACTIVE), any(Pageable.class)))
				.thenReturn(List.of());
		when(placeRepository.findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(
				eq("역삼역"), any(Pageable.class)))
				.thenReturn(List.of(yeoksam));

		service().search("역삼역", 1);

		verifyNoInteractions(placeKeywordSearcher);
	}

	@Test
	void rejectsBlankQuery() {
		assertThatThrownBy(() -> service().search(" ", 10)).isInstanceOf(BusinessException.class);
	}

	private AdministrativeArea area(Long id, String name) {
		AdministrativeArea area = mock(AdministrativeArea.class);
		when(area.getId()).thenReturn(id);
		when(area.getName()).thenReturn(name);
		return area;
	}

	private Place place(Long id, String name) {
		Place place = Place.create("POI042", name, PlaceCategory.POPULATION_DENSE,
				new BigDecimal("37.5"), new BigDecimal("127.03"), "seoul-citydata");
		org.springframework.test.util.ReflectionTestUtils.setField(place, "id", id);
		return place;
	}

	private LocationQueryService service() {
		return new LocationQueryService(
				resolver, addressResolver, administrativeAreaRepository, placeRepository, placeKeywordSearcher);
	}
}
