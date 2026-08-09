package com.ojimasam.safy.live.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface JpaStreamRecordingRepository
		extends JpaRepository<StreamRecording, Long>, StreamRecordingRepository {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select recording from StreamRecording recording where recording.providerEgressId = :egressId")
	Optional<StreamRecording> findByProviderEgressIdForUpdate(@Param("egressId") String providerEgressId);
}
