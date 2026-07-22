package com.ojimasam.safy.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.admin.domain.ControlCenter;
import com.ojimasam.safy.admin.domain.ControlCenterRepository;

public interface JpaControlCenterRepository
		extends JpaRepository<ControlCenter, Long>, ControlCenterRepository {
}
