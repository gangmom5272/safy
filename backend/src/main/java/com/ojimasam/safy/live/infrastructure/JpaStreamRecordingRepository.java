package com.ojimasam.safy.live.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.live.domain.StreamRecording;
import com.ojimasam.safy.live.domain.StreamRecordingRepository;

public interface JpaStreamRecordingRepository
		extends JpaRepository<StreamRecording, Long>, StreamRecordingRepository {
}
