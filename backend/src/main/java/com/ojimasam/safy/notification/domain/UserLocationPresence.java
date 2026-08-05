package com.ojimasam.safy.notification.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.grid.domain.GridCellCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "user_location_presences",
		indexes = @Index(
				name = "idx_user_location_presence_target",
				columnList = "place_id, last_location_at, last_seen_at"
		)
)
public class UserLocationPresence extends BaseEntity {
	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "grid_cell_code", nullable = false, length = 24)
	private String gridCellCode;

	@Column(name = "place_id")
	private Long placeId;

	@Column(name = "last_location_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant lastLocationAt;

	@Column(name = "last_seen_at", nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant lastSeenAt;

	protected UserLocationPresence() {
	}

	private UserLocationPresence(Long userId, BigDecimal latitude, BigDecimal longitude,
			Long placeId, Instant now) {
		this.userId = userId;
		update(latitude, longitude, placeId, now);
	}

	public static UserLocationPresence create(Long userId, BigDecimal latitude, BigDecimal longitude,
			Long placeId, Instant now) {
		if (userId == null) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
		return new UserLocationPresence(userId, latitude, longitude, placeId, now);
	}

	public void update(BigDecimal latitude, BigDecimal longitude, Long placeId, Instant now) {
		validateCoordinates(latitude, longitude);
		if (now == null) throw new IllegalArgumentException("위치 확인 시각은 필수입니다.");
		this.latitude = latitude;
		this.longitude = longitude;
		this.gridCellCode = GridCellCode.of(latitude, longitude).value();
		this.placeId = placeId;
		this.lastLocationAt = now;
		this.lastSeenAt = now;
	}

	private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
		if (latitude == null || longitude == null
				|| latitude.compareTo(BigDecimal.valueOf(-90)) < 0
				|| latitude.compareTo(BigDecimal.valueOf(90)) > 0
				|| longitude.compareTo(BigDecimal.valueOf(-180)) < 0
				|| longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
			throw new IllegalArgumentException("위도와 경도가 올바르지 않습니다.");
		}
	}

	public Long getUserId() { return userId; }
	public Long getPlaceId() { return placeId; }
	public Instant getLastLocationAt() { return lastLocationAt; }
	public Instant getLastSeenAt() { return lastSeenAt; }
}
