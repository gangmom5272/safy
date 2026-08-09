package com.ojimasam.safy.publicdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.publicdata.domain.DisasterMessage;
import com.ojimasam.safy.publicdata.domain.DisasterMessageRepository;

public interface JpaDisasterMessageRepository
		extends JpaRepository<DisasterMessage, Long>, DisasterMessageRepository {
}
