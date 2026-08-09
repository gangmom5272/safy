package com.ojimasam.safy.controlcenter.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;

public interface ControlCenterRepository {
	ControlCenter save(ControlCenter controlCenter);

	Optional<ControlCenter> findById(Long id);

	Optional<ControlCenter> findByIdAndStatus(Long id, ControlCenterStatus status);

	boolean existsByCode(String code);

	Page<ControlCenter> findAll(Pageable pageable);

	Page<ControlCenter> findAllByStatus(ControlCenterStatus status, Pageable pageable);
}
