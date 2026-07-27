package com.ojimasam.safy.realtime.infrastructure;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.realtime.domain.RealtimeEvent;
import com.ojimasam.safy.realtime.domain.RealtimeEventRepository;

public interface JpaRealtimeEventRepository extends JpaRepository<RealtimeEvent, Long>, RealtimeEventRepository {
	@Override
	List<RealtimeEvent> findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(
			Long recipientUserId, Long lastEventId, Pageable pageable);
}
