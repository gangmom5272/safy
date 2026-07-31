package com.ojimasam.safy.realtime.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;

public interface RealtimeEventRepository {
	RealtimeEvent save(RealtimeEvent event);
	List<RealtimeEvent> findByRecipientUserIdAndIdGreaterThanOrderByIdAsc(
			Long recipientUserId, Long lastEventId, Pageable pageable);
	/** 제보 멈춤(STALLED) 알림을 스케줄러 주기마다 반복 발송하지 않기 위한 중복 확인. */
	boolean existsByReportIdAndEventType(Long reportId, com.ojimasam.safy.realtime.domain.type.RealtimeEventType eventType);
}
