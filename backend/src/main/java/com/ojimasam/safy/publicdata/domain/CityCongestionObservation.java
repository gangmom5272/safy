package com.ojimasam.safy.publicdata.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ojimasam.safy.place.domain.Place;
import com.ojimasam.safy.place.domain.type.CongestionLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 서울시 실시간 도시데이터의 지역별 인구 밀집 관측값. 추가만 하고 수정하지 않는다.
 * (place, observedAt) 유일 제약이 재폴링으로 인한 중복 적재를 막는다.
 *
 * 이 유일 제약이 만드는 btree 인덱스가 지역별 최신 1건 조회(역방향 스캔)와 추이 조회를 함께 담당하므로
 * 같은 컬럼 조합의 별도 인덱스는 두지 않는다. 5분마다 121행이 들어오는 테이블에서
 * 중복 인덱스는 쓰기 비용과 용량만 늘린다.
 *
 * 좌표는 두지 않는다. 원본 인구 블록이 지역 좌표를 주지 않고, 지역 중심점은 지역당 하나뿐이라
 * places 에 저장할 값이다. 관측 행마다 같은 값을 반복할 이유가 없다.
 * (행사의 좌표는 행사장마다 다른 값이므로 events 에는 그대로 둔다.)
 */
@Entity
@Table(
		name = "city_congestion_observations",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_city_congestion_observations_place_observed_at",
				columnNames = {"place_id", "observed_at"}
		)
)
@EntityListeners(AuditingEntityListener.class)
public class CityCongestionObservation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	private Place place;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_run_id", nullable = false)
	private CollectionRun collectionRun;

	/** 원본 식별자를 그대로 남겨서, place 매핑이 없던 지역을 나중에 복구할 수 있게 한다. */
	@Column(name = "area_code", nullable = false, length = 100)
	private String areaCode;

	@Column(name = "area_name", nullable = false, columnDefinition = "text")
	private String areaName;

	@Enumerated(EnumType.STRING)
	@Column(name = "congestion_level", nullable = false, length = 30)
	private CongestionLevel congestionLevel;

	@Column(name = "congestion_message", columnDefinition = "text")
	private String congestionMessage;

	@Column(name = "population_min")
	private Integer populationMin;

	@Column(name = "population_max")
	private Integer populationMax;

	@Column(name = "observed_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant observedAt;

	@Column(name = "collected_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant collectedAt;

	@Column(nullable = false, length = 50)
	private String source;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant createdAt;

	protected CityCongestionObservation() {
	}

	private CityCongestionObservation(
			Place place,
			CollectionRun collectionRun,
			String areaCode,
			String areaName,
			CongestionLevel congestionLevel,
			String congestionMessage,
			Integer populationMin,
			Integer populationMax,
			Instant observedAt,
			Instant collectedAt,
			String source
	) {
		this.place = place;
		this.collectionRun = collectionRun;
		this.areaCode = areaCode;
		this.areaName = areaName;
		this.congestionLevel = congestionLevel;
		this.congestionMessage = congestionMessage;
		this.populationMin = populationMin;
		this.populationMax = populationMax;
		this.observedAt = observedAt;
		this.collectedAt = collectedAt;
		this.source = source;
	}

	public static CityCongestionObservation create(
			Place place,
			CollectionRun collectionRun,
			String areaCode,
			String areaName,
			CongestionLevel congestionLevel,
			String congestionMessage,
			Integer populationMin,
			Integer populationMax,
			Instant observedAt,
			Instant collectedAt,
			String source
	) {
		return new CityCongestionObservation(
				place,
				collectionRun,
				areaCode,
				areaName,
				congestionLevel,
				congestionMessage,
				populationMin,
				populationMax,
				observedAt,
				collectedAt,
				source
		);
	}

	public Long getId() {
		return id;
	}

	public Place getPlace() {
		return place;
	}

	public CollectionRun getCollectionRun() {
		return collectionRun;
	}

	public String getAreaCode() {
		return areaCode;
	}

	public String getAreaName() {
		return areaName;
	}

	public CongestionLevel getCongestionLevel() {
		return congestionLevel;
	}

	public String getCongestionMessage() {
		return congestionMessage;
	}

	public Integer getPopulationMin() {
		return populationMin;
	}

	public Integer getPopulationMax() {
		return populationMax;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public Instant getCollectedAt() {
		return collectedAt;
	}

	public String getSource() {
		return source;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
