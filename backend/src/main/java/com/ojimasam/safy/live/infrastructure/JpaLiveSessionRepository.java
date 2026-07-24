package com.ojimasam.safy.live.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;

import jakarta.persistence.LockModeType;

public interface JpaLiveSessionRepository extends JpaRepository<LiveSession, UUID>, LiveSessionRepository {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from LiveSession s where s.id = :id")
	Optional<LiveSession> findByIdForUpdate(@Param("id") UUID id);
}
