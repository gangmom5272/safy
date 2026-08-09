package com.ojimasam.safy.place.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.common.config.JpaAuditingConfig;
import com.ojimasam.safy.place.domain.Place;

/**
 * seed SQL 이 엔티티가 만든 스키마에 실제로 적용되는지 확인한다.
 * 121행 INSERT 는 눈으로 검증할 수 없고, Place 에 컬럼이 추가되면 조용히 깨지는 종류의 파일이다.
 *
 * 커버 범위: 컬럼 목록이 스키마와 맞는지, 121행이 다 들어가는지,
 *   '·'·괄호가 섞인 이름과 카테고리 enum 값이 그대로 저장되는지,
 *   서울시 영역 shapefile 에서 계산한 좌표가 서울 범위 안에 들어오는지,
 *   영역 폴리곤이 GeoJSON 으로 온전히 들어갔는지.
 *
 * 커버하지 않는 것: 끝의 ON CONFLICT ... DO UPDATE 절.
 *   H2 가 이 구문을 지원하지 않아 테스트에서는 잘라내고 INSERT 본문만 실행한다.
 *   (Postgres 전용 구문이며 seoul-administrative-areas.sql 과 같은 패턴이다.)
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class SeoulPlacesSeedTest {
	@Autowired
	private DataSource dataSource;

	@Autowired
	private JpaPlaceRepository placeRepository;

	@Test
	void insertsAll121PlacesWithSeoulCoordinates() throws Exception {
		new JdbcTemplate(dataSource).execute(insertStatementOf("db/seed/seoul-places.sql"));

		List<Place> places = placeRepository.findAllByActiveTrueOrderByNameAsc();

		assertThat(places).hasSize(121);
		assertThat(places).allSatisfy(place -> {
			assertThat(place.getExternalPlaceId()).startsWith("POI");
			assertThat(place.getCategory()).isNotNull();
			// 좌표가 뒤바뀌거나 국내 좌표계(수십만 단위)로 들어오면 지도가 엉뚱한 곳을 가리킨다.
			assertThat(place.getCenterLatitude())
					.as("%s 위도", place.getName())
					.isBetween(new BigDecimal("37.4"), new BigDecimal("37.7"));
			assertThat(place.getCenterLongitude())
					.as("%s 경도", place.getName())
					.isBetween(new BigDecimal("126.7"), new BigDecimal("127.25"));
			// 폴리곤은 최대 24KB 라 컬럼 폭이 좁으면 잘린 채 저장된다.
			assertThat(place.getBoundaryGeoJson())
					.as("%s 영역", place.getName())
					.startsWith("{\"type\":\"Polygon\",\"coordinates\":[[[")
					.endsWith("]]]}");
		});

		assertThat(placeRepository.findByExternalPlaceId("POI001"))
				.get()
				.satisfies(place -> assertThat(place.getName()).isEqualTo("강남 MICE 관광특구"));
		// '·' 와 괄호가 들어간 이름이 깨지지 않아야 한다.
		assertThat(placeRepository.findByExternalPlaceId("POI009"))
				.get()
				.satisfies(place -> assertThat(place.getName()).isEqualTo("광화문·덕수궁"));
		assertThat(placeRepository.findByExternalPlaceId("POI051"))
				.get()
				.satisfies(place -> assertThat(place.getName()).isEqualTo("총신대입구(이수)역"));
		// 결번은 등록되지 않아야 한다. POI113(청와대)은 2026-03-12 자로 삭제된 지역이다.
		assertThat(placeRepository.findByExternalPlaceId("POI022")).isEmpty();
		assertThat(placeRepository.findByExternalPlaceId("POI113")).isEmpty();

		// 위경도가 뒤바뀌면 통과할 수 없는 실제 좌표로 고정한다(강남역 = 37.49/127.02).
		assertThat(placeRepository.findByExternalPlaceId("POI014"))
				.get()
				.satisfies(place -> {
					assertThat(place.getName()).isEqualTo("강남역");
					assertThat(place.getCenterLatitude()).isEqualByComparingTo("37.4988566");
					assertThat(place.getCenterLongitude()).isEqualByComparingTo("127.0281337");
				});

		// 폴리곤 전체가 온전한지 가장 작은 지역으로 확인한다.
		// GeoJSON 은 [경도, 위도] 순서이고 링이 닫혀 있어야 한다(첫 점 == 마지막 점).
		assertThat(placeRepository.findByExternalPlaceId("POI130"))
				.get()
				.satisfies(place -> assertThat(place.getBoundaryGeoJson()).isEqualTo(
						"{\"type\":\"Polygon\",\"coordinates\":[["
								+ "[126.975792,37.5703508],"
								+ "[126.9757685,37.563857],"
								+ "[126.9780773,37.5637222],"
								+ "[126.9781032,37.5703438],"
								+ "[126.975792,37.5703508]"
								+ "]]}"));

		// 가장 복잡한 지역(남산공원 1084개 정점)이 잘리지 않았는지 길이로 확인한다.
		assertThat(placeRepository.findByExternalPlaceId("POI091"))
				.get()
				.satisfies(place -> {
					assertThat(place.getName()).isEqualTo("남산공원");
					assertThat(place.getBoundaryGeoJson().length()).isGreaterThan(20000);
				});
	}

	/** H2 가 지원하지 않는 ON CONFLICT 절을 잘라내고 INSERT 본문만 남긴다. */
	private String insertStatementOf(String location) throws Exception {
		String script = new String(
				new ClassPathResource(location).getContentAsByteArray(),
				StandardCharsets.UTF_8
		);
		// 헤더 주석에도 'ON CONFLICT' 라는 말이 나오므로 절의 형태까지 맞춰서 찾는다.
		int onConflict = script.indexOf("\nON CONFLICT (");
		assertThat(onConflict).as("seed 에 ON CONFLICT upsert 절이 있어야 한다").isPositive();
		return script.substring(0, onConflict);
	}
}
