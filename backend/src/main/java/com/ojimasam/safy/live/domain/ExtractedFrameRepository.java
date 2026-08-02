package com.ojimasam.safy.live.domain;

import java.util.Optional;

public interface ExtractedFrameRepository {
	ExtractedFrame save(ExtractedFrame extractedFrame);
	boolean existsByMediaAssetId(Long mediaAssetId);
	Optional<ExtractedFrame> findByMediaAssetId(Long mediaAssetId);
	Optional<ExtractedFrame> findTopByStreamRecordingIdOrderBySequenceNumberDesc(Long streamRecordingId);
}
