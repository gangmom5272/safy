package com.ojimasam.safy.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;

public interface JpaOperatorPresenceRepository
		extends JpaRepository<OperatorPresence, Long>, OperatorPresenceRepository {
}
