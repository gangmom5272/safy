package com.ojimasam.safy.analysis.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.analysis.domain.MediaProcessingJob;
import com.ojimasam.safy.analysis.domain.MediaProcessingJobRepository;
import com.ojimasam.safy.analysis.domain.type.MediaProcessingJobStatus;

import jakarta.persistence.LockModeType;

public interface JpaMediaProcessingJobRepository
		extends JpaRepository<MediaProcessingJob, Long>, MediaProcessingJobRepository {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from MediaProcessingJob job where job.id = :id")
	Optional<MediaProcessingJob> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MediaProcessingJob> findTopByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
			Collection<MediaProcessingJobStatus> statuses, Instant now);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<MediaProcessingJob> findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
			MediaProcessingJobStatus status, Instant startedBefore);

	@Override
	@Query("""
			select job
			from MediaProcessingJob job, MediaAsset asset
			where job.mediaAssetId = asset.id
			  and asset.reportId = :reportId
			order by job.id asc
			""")
	List<MediaProcessingJob> findAllByReportId(@Param("reportId") Long reportId);
}
