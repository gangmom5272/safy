package com.ojimasam.safy.admin.domain;

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
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, unique = true, length = 30)
	private String code;
	@Column(nullable = false, length = 100)
	private String name;
	@Column(nullable = false, length = 30)
	private String centerType;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ControlCenterStatus status;
	private Integer capacityLimit;
	private String address;
	@Column(length = 30)
	private String phoneNumber;
	protected ControlCenter() {}
}
