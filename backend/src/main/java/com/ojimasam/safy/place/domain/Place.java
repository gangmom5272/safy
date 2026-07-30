package com.ojimasam.safy.place.domain;

import java.math.BigDecimal;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.place.domain.type.PlaceCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서울시 실시간 도시데이터가 다루는 주요 지역(핫스팟) 121곳.
 * 정적 마스터 데이터이므로 seed SQL 로 관리하며, 수집 파이프라인은 이 테이블을 갱신하지 않는다.
 *
 * externalPlaceId 는 서울시 API 의 AREA_CD 다. 수집 데이터를 지역에 붙이는 매핑 키이면서,
 * 스케줄러가 FastAPI 요청에 실어 보내는 수집 대상 목록의 단일 진실 원천이다.
 */
@Entity
@Table(name = "places")
public class Place extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_place_id", nullable = false, unique = true, length = 255)
	private String externalPlaceId;

	@Column(nullable = false, length = 255)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PlaceCategory category;

	/**
	 * 중심 좌표는 지도 표시에만 쓰이고 수집에는 필요하지 않아 비어 있을 수 있다.
	 * 서울시가 배포하는 121장소 목록에 좌표가 없어서, 지오코딩과 검수를 거쳐 나중에 채운다.
	 * 좌표를 기다리는 동안에도 수집은 진행되어야 한다(관측값은 지나가면 다시 얻을 수 없다).
	 */
	@Column(name = "center_latitude", precision = 10, scale = 7)
	private BigDecimal centerLatitude;

	@Column(name = "center_longitude", precision = 10, scale = 7)
	private BigDecimal centerLongitude;

	/**
	 * 지역 영역의 GeoJSON Polygon. 서울시가 배포한 영역 shapefile 을 변환한 값이다.
	 *
	 * 중심점만으로는 '이 좌표가 이 지역 안인가'를 판정할 수 없다. 지역마다 크기와 모양이 크게 달라
	 * (강남역 9개 정점, 남산공원 1084개 정점) 하나의 반경으로 대체할 수 없기 때문이다.
	 * 제보 좌표를 혼잡도에 연결할 때 이 도형으로 판정한다.
	 *
	 * 값이 최대 24KB 이므로 목록 응답에 실어 보내지 않는다(서버 내부 판정용).
	 * administrative_areas.boundary_geojson 과 같이 text 로 둔다(H2 가 jsonb 리터럴을 못 받는다).
	 */
	@Column(name = "boundary_geojson", columnDefinition = "text")
	private String boundaryGeoJson;

	@Column(length = 50)
	private String source;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected Place() {
	}

	private Place(
			String externalPlaceId,
			String name,
			PlaceCategory category,
			BigDecimal centerLatitude,
			BigDecimal centerLongitude,
			String source,
			boolean active
	) {
		this.externalPlaceId = externalPlaceId;
		this.name = name;
		this.category = category;
		this.centerLatitude = centerLatitude;
		this.centerLongitude = centerLongitude;
		this.source = source;
		this.active = active;
	}

	public static Place create(
			String externalPlaceId,
			String name,
			PlaceCategory category,
			BigDecimal centerLatitude,
			BigDecimal centerLongitude,
			String source
	) {
		return new Place(externalPlaceId, name, category, centerLatitude, centerLongitude, source, true);
	}

	public Long getId() {
		return id;
	}

	public String getExternalPlaceId() {
		return externalPlaceId;
	}

	public String getName() {
		return name;
	}

	public PlaceCategory getCategory() {
		return category;
	}

	public BigDecimal getCenterLatitude() {
		return centerLatitude;
	}

	public BigDecimal getCenterLongitude() {
		return centerLongitude;
	}

	public String getBoundaryGeoJson() {
		return boundaryGeoJson;
	}

	public String getSource() {
		return source;
	}

	public boolean isActive() {
		return active;
	}
}
