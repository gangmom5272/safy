package com.ojimasam.safy.live.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.live.domain.LiveSession;
import com.ojimasam.safy.live.domain.LiveSessionRepository;
import com.ojimasam.safy.live.domain.type.LiveSessionStatus;
import com.ojimasam.safy.report.domain.type.ReportOperatorAssignmentStatus;

import jakarta.persistence.LockModeType;
import com.ojimasam.safy.admin.domain.AdminCenterMembership;
import com.ojimasam.safy.report.domain.ReportOperatorAssignment;

public interface JpaLiveSessionRepository extends JpaRepository<LiveSession, UUID>, LiveSessionRepository {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from LiveSession s where s.id = :id")
	Optional<LiveSession> findByIdForUpdate(@Param("id") UUID id);

	@Override
	@Query("""
			select session
			from LiveSession session
			where session.status in :liveStatuses
			  and session.createdAt < :cursor
			  and exists (
				select assignment.id
				from ReportOperatorAssignment assignment
				where assignment.report.id = session.reportId
				  and assignment.operator.id = :operatorId
				  and assignment.status in :assignmentStatuses
				  and exists (
					select membership.id
					from AdminCenterMembership membership
					where membership.user.id = :operatorId
					  and membership.isPrimary = true
					  and membership.status = com.ojimasam.safy.admin.domain.type.MembershipStatus.ACTIVE
					  and membership.controlCenter.id = assignment.route.controlCenter.id
				  )
			  )
			order by session.createdAt desc
			""")
	List<LiveSession> findAccessibleForOperator(
			@Param("operatorId") Long operatorId,
			@Param("liveStatuses") Collection<LiveSessionStatus> liveStatuses,
			@Param("assignmentStatuses") Collection<ReportOperatorAssignmentStatus> assignmentStatuses,
			@Param("cursor") Instant cursor,
			Pageable pageable
	);
}
