package com.ojimasam.safy.analysis.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobType;

public interface MediaProcessingJobRepository {
	MediaProcessingJob save(MediaProcessingJob job);

	Optional<MediaProcessingJob> findByMediaAssetIdAndJobType(
			Long mediaAssetId, MediaProcessingJobType jobType);

	Optional<MediaProcessingJob> findByIdForUpdate(Long id);

	Optional<MediaProcessingJob> findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
			Collection<MediaProcessingJobStatus> statuses, Instant now);

	List<MediaProcessingJob> findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
			MediaProcessingJobStatus status, Instant startedBefore);

	List<MediaProcessingJob> findAllByReportId(Long reportId);
}
