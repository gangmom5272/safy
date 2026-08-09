package com.ojimasam.safy.admin.application;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.OperatorPresenceRepository;

/**
 * 근무 상태 행의 최초 생성만 담당한다. 별도 트랜잭션으로 분리한 이유는,
 * 동시 최초 접속(탭 두 개 등)에서 unique 제약 위반이 나더라도 호출한 쪽 트랜잭션을
 * 오염시키지 않기 위해서다 — PostgreSQL 은 문장 실패 시 트랜잭션 전체가 abort 되므로
 * 같은 트랜잭션 안에서 잡아 재조회하는 방식은 동작하지 않는다.
 * 자기 호출은 프록시를 우회하므로 서비스와 다른 빈으로 둔다.
 */
@Component
public class OperatorPresenceRegistrar {
	private final OperatorPresenceRepository presenceRepository;

	public OperatorPresenceRegistrar(OperatorPresenceRepository presenceRepository) {
		this.presenceRepository = presenceRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void create(Long operatorId, Instant now) {
		presenceRepository.save(OperatorPresence.connect(operatorId, now));
	}
}
