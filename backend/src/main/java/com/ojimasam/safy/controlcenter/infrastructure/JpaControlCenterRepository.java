package com.ojimasam.safy.controlcenter.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterRepository;

public interface JpaControlCenterRepository
		extends JpaRepository<ControlCenter, Long>, ControlCenterRepository {
}
