package com.ojimasam.safy.realtime.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;

public interface RealtimeEventRepository {
	RealtimeEvent save(RealtimeEvent event);
	List<RealtimeEvent> findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(
			Long recipientUserId, Long lastEventId, Pageable pageable);
}
