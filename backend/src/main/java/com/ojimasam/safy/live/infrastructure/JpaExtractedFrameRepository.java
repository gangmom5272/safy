package com.ojimasam.safy.live.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.live.domain.ExtractedFrame;
import com.ojimasam.safy.live.domain.ExtractedFrameRepository;

public interface JpaExtractedFrameRepository
		extends JpaRepository<ExtractedFrame, Long>, ExtractedFrameRepository {
}
