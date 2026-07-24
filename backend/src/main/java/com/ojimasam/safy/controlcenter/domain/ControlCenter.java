package com.ojimasam.safy.controlcenter.domain;

import com.ojimasam.safy.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "control_centers")
public class ControlCenter extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 30)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "center_type", nullable = false, length = 30)
	private ControlCenterType centerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ControlCenterStatus status;

	@Column(name = "capacity_limit")
	private Integer capacityLimit;

	@Column(length = 255)
	private String address;

	@Column(name = "phone_number", length = 30)
	private String phoneNumber;

	protected ControlCenter() {
	}

	private ControlCenter(
			String code,
			String name,
			ControlCenterType centerType,
			Integer capacityLimit,
			String address,
			String phoneNumber
	) {
		this.code = code;
		this.name = name;
		this.centerType = centerType;
		this.status = ControlCenterStatus.ACTIVE;
		this.capacityLimit = capacityLimit;
		this.address = address;
		this.phoneNumber = phoneNumber;
	}

	public static ControlCenter create(
			String code,
			String name,
			ControlCenterType centerType,
			Integer capacityLimit,
			String address,
			String phoneNumber
	) {
		validate(code, name, centerType, capacityLimit);
		return new ControlCenter(code.trim(), name.trim(), centerType, capacityLimit, trimToNull(address),
				trimToNull(phoneNumber));
	}

	public void update(
			String name,
			ControlCenterType centerType,
			ControlCenterStatus status,
			Integer capacityLimit,
			String address,
			String phoneNumber
	) {
		validate(code, name, centerType, capacityLimit);
		if (status == null) throw new IllegalArgumentException("관제센터 상태는 필수입니다.");
		this.name = name.trim();
		this.centerType = centerType;
		this.status = status;
		this.capacityLimit = capacityLimit;
		this.address = trimToNull(address);
		this.phoneNumber = trimToNull(phoneNumber);
	}

	public void deactivate() {
		this.status = ControlCenterStatus.INACTIVE;
	}

	private static void validate(String code, String name, ControlCenterType centerType, Integer capacityLimit) {
		if (code == null || code.isBlank()) throw new IllegalArgumentException("관제센터 코드는 필수입니다.");
		if (code.length() > 30) throw new IllegalArgumentException("관제센터 코드는 30자 이하여야 합니다.");
		if (name == null || name.isBlank()) throw new IllegalArgumentException("관제센터 이름은 필수입니다.");
		if (name.length() > 100) throw new IllegalArgumentException("관제센터 이름은 100자 이하여야 합니다.");
		if (centerType == null) throw new IllegalArgumentException("관제센터 유형은 필수입니다.");
		if (capacityLimit != null && capacityLimit < 1) {
			throw new IllegalArgumentException("수용 한도는 1 이상이어야 합니다.");
		}
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public ControlCenterType getCenterType() {
		return centerType;
	}

	public ControlCenterStatus getStatus() {
		return status;
	}

	public Integer getCapacityLimit() {
		return capacityLimit;
	}

	public String getAddress() {
		return address;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}
}
