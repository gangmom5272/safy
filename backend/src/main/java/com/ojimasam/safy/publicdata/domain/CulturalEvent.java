package com.ojimasam.safy.publicdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.place.domain.Place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 서울시 문화 행사. citydata 응답의 EVENT_STTS 에서 수집한다.
 * 원본에 안정적인 행사 식별자가 없어 FastAPI 가 합성한 externalId 를 upsert 키로 사용한다.
 * 원본 기간 문자열(eventPeriod)이 파싱되지 않을 수 있어 startDate/endDate 는 비어 있을 수 있다.
 */
@Entity
@Table(
		name = "events",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_events_source_external_id",
				columnNames = {"source", "external_id"}
		),
		indexes = @Index(name = "idx_events_start_date_end_date", columnList = "start_date, end_date")
)
public class CulturalEvent extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	private Place place;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_run_id", nullable = false)
	private CollectionRun collectionRun;

	@Column(name = "external_id", nullable = false, length = 255)
	private String externalId;

	// 상류 자유 텍스트는 길이 제한을 두지 않는다.
	// varchar 를 넘기면 INSERT 가 실패해 그 수집 배치 전체가 롤백된다.
	@Column(nullable = false, columnDefinition = "text")
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "venue_name", columnDefinition = "text")
	private String venueName;

	@Column(name = "event_period", columnDefinition = "text")
	private String eventPeriod;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "area_code", length = 100)
	private String areaCode;

	@Column(name = "area_name", columnDefinition = "text")
	private String areaName;

	@Column(name = "is_paid")
	private Boolean paid;

	@Column(name = "thumbnail_url", columnDefinition = "text")
	private String thumbnailUrl;

	@Column(name = "detail_url", columnDefinition = "text")
	private String detailUrl;

	@Column(nullable = false, length = 50)
	private String source;

	@Column(name = "collected_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant collectedAt;

	protected CulturalEvent() {
	}

	private CulturalEvent(
			Place place,
			CollectionRun collectionRun,
			String externalId,
			String title,
			String description,
			String venueName,
			String eventPeriod,
			LocalDate startDate,
			LocalDate endDate,
			BigDecimal latitude,
			BigDecimal longitude,
			String areaCode,
			String areaName,
			Boolean paid,
			String thumbnailUrl,
			String detailUrl,
			String source,
			Instant collectedAt
	) {
		this.place = place;
		this.collectionRun = collectionRun;
		this.externalId = externalId;
		this.title = title;
		this.description = description;
		this.venueName = venueName;
		this.eventPeriod = eventPeriod;
		this.startDate = startDate;
		this.endDate = endDate;
		this.latitude = latitude;
		this.longitude = longitude;
		this.areaCode = areaCode;
		this.areaName = areaName;
		this.paid = paid;
		this.thumbnailUrl = thumbnailUrl;
		this.detailUrl = detailUrl;
		this.source = source;
		this.collectedAt = collectedAt;
	}

	public static CulturalEvent create(
			Place place,
			CollectionRun collectionRun,
			String externalId,
			String title,
			String description,
			String venueName,
			String eventPeriod,
			LocalDate startDate,
			LocalDate endDate,
			BigDecimal latitude,
			BigDecimal longitude,
			String areaCode,
			String areaName,
			Boolean paid,
			String thumbnailUrl,
			String detailUrl,
			String source,
			Instant collectedAt
	) {
		return new CulturalEvent(
				place,
				collectionRun,
				externalId,
				title,
				description,
				venueName,
				eventPeriod,
				startDate,
				endDate,
				latitude,
				longitude,
				areaCode,
				areaName,
				paid,
				thumbnailUrl,
				detailUrl,
				source,
				collectedAt
		);
	}

	/** 같은 externalId 로 다시 수집된 행사의 변경 가능한 값을 갱신한다. */
	public void refreshFrom(CulturalEvent latest) {
		this.place = latest.place;
		this.collectionRun = latest.collectionRun;
		this.title = latest.title;
		this.description = latest.description;
		this.venueName = latest.venueName;
		this.eventPeriod = latest.eventPeriod;
		this.startDate = latest.startDate;
		this.endDate = latest.endDate;
		this.latitude = latest.latitude;
		this.longitude = latest.longitude;
		this.areaCode = latest.areaCode;
		this.areaName = latest.areaName;
		this.paid = latest.paid;
		this.thumbnailUrl = latest.thumbnailUrl;
		this.detailUrl = latest.detailUrl;
		this.collectedAt = latest.collectedAt;
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

	public String getExternalId() {
		return externalId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getVenueName() {
		return venueName;
	}

	public String getEventPeriod() {
		return eventPeriod;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public String getAreaCode() {
		return areaCode;
	}

	public String getAreaName() {
		return areaName;
	}

	public Boolean getPaid() {
		return paid;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public String getDetailUrl() {
		return detailUrl;
	}

	public String getSource() {
		return source;
	}

	public Instant getCollectedAt() {
		return collectedAt;
	}
}
