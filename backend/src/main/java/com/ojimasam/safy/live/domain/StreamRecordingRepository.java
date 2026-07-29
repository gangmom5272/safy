package com.ojimasam.safy.live.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ojimasam.safy.live.domain.type.StreamRecordingStatus;

public interface StreamRecordingRepository {
	StreamRecording save(StreamRecording recording);

	Optional<StreamRecording> findByProviderEgressId(String providerEgressId);

	boolean existsByLiveSessionIdAndStatusIn(UUID liveSessionId, Collection<StreamRecordingStatus> statuses);

	List<StreamRecording> findAllByLiveSessionIdAndStatusIn(
			UUID liveSessionId, Collection<StreamRecordingStatus> statuses);
}
