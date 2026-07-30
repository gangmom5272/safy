package com.ojimasam.safy.publicdata.domain;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * 긴급 재난 문자. 지도에 직접 표시하지 않고, 수신 지역명을 기준으로
 * 해당 지역 제보의 위험도 가중치를 올리는 데 사용한다.
 * 원본 API가 좌표를 주지 않으므로 위치는 regions 로만 표현된다.
 */
@Entity
@Table(
		name = "disaster_messages",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_disaster_messages_source_external_id",
				columnNames = {"source", "external_id"}
		),
		indexes = @Index(name = "idx_disaster_messages_sent_at", columnList = "sent_at desc")
)
@EntityListeners(AuditingEntityListener.class)
public class DisasterMessage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_run_id", nullable = false)
	private CollectionRun collectionRun;

	@Column(name = "external_id", nullable = false, length = 255)
	private String externalId;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	/** 원본의 쉼표 구분 수신지역명을 FastAPI가 배열로 정규화해서 보낸다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> regions;

	// 상류 자유 텍스트는 길이 제한을 두지 않는다. varchar 를 넘기면 INSERT 가 실패한다.
	@Column(name = "disaster_type", columnDefinition = "text")
	private String disasterType;

	@Column(name = "emergency_level", nullable = false, columnDefinition = "text")
	private String emergencyLevel;

	@Column(name = "sent_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant sentAt;

	@Column(nullable = false, length = 50)
	private String source;

	@Column(name = "collected_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant collectedAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant createdAt;

	protected DisasterMessage() {
	}

	private DisasterMessage(
			CollectionRun collectionRun,
			String externalId,
			String message,
			List<String> regions,
			String disasterType,
			String emergencyLevel,
			Instant sentAt,
			String source,
			Instant collectedAt
	) {
		this.collectionRun = collectionRun;
		this.externalId = externalId;
		this.message = message;
		this.regions = regions;
		this.disasterType = disasterType;
		this.emergencyLevel = emergencyLevel;
		this.sentAt = sentAt;
		this.source = source;
		this.collectedAt = collectedAt;
	}

	public static DisasterMessage create(
			CollectionRun collectionRun,
			String externalId,
			String message,
			List<String> regions,
			String disasterType,
			String emergencyLevel,
			Instant sentAt,
			String source,
			Instant collectedAt
	) {
		return new DisasterMessage(
				collectionRun,
				externalId,
				message,
				regions,
				disasterType,
				emergencyLevel,
				sentAt,
				source,
				collectedAt
		);
	}

	public Long getId() {
		return id;
	}

	public CollectionRun getCollectionRun() {
		return collectionRun;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getMessage() {
		return message;
	}

	public List<String> getRegions() {
		return regions;
	}

	public String getDisasterType() {
		return disasterType;
	}

	public String getEmergencyLevel() {
		return emergencyLevel;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public String getSource() {
		return source;
	}

	public Instant getCollectedAt() {
		return collectedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
