package com.ojimasam.safy.administrativearea.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;

public interface JpaAdministrativeAreaRepository
		extends JpaRepository<AdministrativeArea, Long>, AdministrativeAreaRepository {
}
