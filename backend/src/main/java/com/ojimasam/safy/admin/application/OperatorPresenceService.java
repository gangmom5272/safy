package com.ojimasam.safy.admin.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.application.dto.OperatorPresenceResponse;
import com.ojimasam.safy.admin.application.event.OperatorUnavailableEvent;
import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

/**
 * 담당자 근무 상태 관리. 로그인 자체는 신호로 쓰지 않는다 — 관제 화면이 열리면
 * SSE 구독이 접속 신호가 되고, 연결이 끊긴 채 유예가 지나면 스위퍼가 오프라인으로 내린다.
 */
@Service
public class OperatorPresenceService {
	private final OperatorPresenceRepository presenceRepository;
	private final OperatorPresenceRegistrar presenceRegistrar;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public OperatorPresenceService(OperatorPresenceRepository presenceRepository,
			OperatorPresenceRegistrar presenceRegistrar,
			ApplicationEventPublisher eventPublisher) {
		this(presenceRepository, presenceRegistrar, eventPublisher, Clock.systemUTC());
	}

	OperatorPresenceService(OperatorPresenceRepository presenceRepository,
			OperatorPresenceRegistrar presenceRegistrar,
			ApplicationEventPublisher eventPublisher, Clock clock) {
		this.presenceRepository = presenceRepository;
		this.presenceRegistrar = presenceRegistrar;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/** SSE 구독 시점의 접속 신호. 오프라인이었다면 근무중으로 복귀시키고 자리비움은 유지한다. */
	@Transactional
	public void markConnected(Long operatorId) {
		Instant now = clock.instant();
		OperatorPresence presence = findOrCreate(operatorId, now);
		presence.markConnected(now);
	}

	/** 연결이 살아있는 담당자의 최근 접속 시각 갱신. 접속 이력이 없으면 무시한다. */
	@Transactional
	public void touch(Long operatorId) {
		presenceRepository.findByOperatorId(operatorId)
				.ifPresent(presence -> presence.touch(clock.instant()));
	}

	/**
	 * 오프라인 전환. 스위퍼가 스냅샷으로 판단한 뒤 호출하므로, 트랜잭션 안에서 최근 접속
	 * 시각을 재검증해 그 사이 재접속한 담당자를 내리지 않는다. 실제로 전환될 때만
	 * 이벤트를 발행해 미확인 배정 회수를 트리거한다.
	 */
	@Transactional
	public void markOffline(Long operatorId, Instant lastSeenBefore) {
		if (lastSeenBefore == null) throw new IllegalArgumentException("오프라인 판정 기준 시각은 필수입니다.");
		presenceRepository.findByOperatorId(operatorId).ifPresent(presence -> {
			if (presence.getStatus() == OperatorPresenceStatus.OFFLINE) return;
			if (!presence.getLastSeenAt().isBefore(lastSeenBefore)) return;
			presence.change(OperatorPresenceStatus.OFFLINE, clock.instant());
			eventPublisher.publishEvent(new OperatorUnavailableEvent(operatorId, OperatorPresenceStatus.OFFLINE));
		});
	}

	/**
	 * 담당자가 직접 근무 상태를 바꾼다. 제보를 받을 수 없는 상태(자리비움·오프라인)로
	 * 새로 넘어갈 때마다 미확인 배정 회수를 트리거한다 — 자리를 비운 사람이 확인도 안 한
	 * 제보를 들고 있으면 타임아웃이 돌 때까지 아무도 보지 않는다.
	 */
	@Transactional
	public OperatorPresenceResponse changeStatus(Long operatorId, OperatorPresenceStatus status) {
		if (status == null) throw new IllegalArgumentException("근무 상태는 필수입니다.");
		Instant now = clock.instant();
		OperatorPresence presence = findOrCreate(operatorId, now);
		boolean becameUnavailable = status != OperatorPresenceStatus.ON_DUTY
				&& presence.getStatus() != status;
		presence.change(status, now);
		if (becameUnavailable) eventPublisher.publishEvent(new OperatorUnavailableEvent(operatorId, status));
		return OperatorPresenceResponse.from(presence);
	}

	@Transactional(readOnly = true)
	public OperatorPresenceResponse getMine(Long operatorId) {
		return presenceRepository.findByOperatorId(operatorId)
				.map(OperatorPresenceResponse::from)
				.orElseGet(OperatorPresenceResponse::offline);
	}

	private OperatorPresence findOrCreate(Long operatorId, Instant now) {
		return presenceRepository.findByOperatorId(operatorId).orElseGet(() -> {
			try {
				// 별도 트랜잭션에서 삽입한다. 동시 최초 접속(탭 두 개 등)으로 충돌해도
				// 롤백되는 것은 안쪽 트랜잭션뿐이라 이 트랜잭션은 그대로 쓸 수 있다.
				presenceRegistrar.create(operatorId, now);
			} catch (DataIntegrityViolationException exception) {
				// 다른 요청이 먼저 만들었다 — 아래에서 그 행을 읽어 쓴다.
			}
			return presenceRepository.findByOperatorId(operatorId)
					.orElseThrow(() -> new IllegalStateException("담당자 근무 상태 저장에 실패했습니다."));
		});
	}
}
