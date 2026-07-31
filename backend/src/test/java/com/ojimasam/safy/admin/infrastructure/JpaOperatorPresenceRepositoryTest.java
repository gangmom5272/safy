package com.ojimasam.safy.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.ojimasam.safy.admin.domain.OperatorPresence;
import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;
import com.ojimasam.safy.common.config.JpaAuditingConfig;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class JpaOperatorPresenceRepositoryTest {
	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Autowired
	private JpaOperatorPresenceRepository repository;

	@Test
	void savesPresencePerOperator() {
		repository.saveAndFlush(OperatorPresence.connect(30L, NOW));

		List<OperatorPresence> found = repository.findAllByOperatorIdIn(List.of(30L));

		assertThat(found).hasSize(1);
		assertThat(found.getFirst().getStatus()).isEqualTo(OperatorPresenceStatus.ON_DUTY);
		assertThat(found.getFirst().getLastSeenAt()).isEqualTo(NOW);
	}

	/**
	 * 동시 최초 접속에서 뒤늦은 삽입이 실패해야 서비스가 기존 행을 재조회하는 경로로 넘어간다.
	 * 이 제약이 없으면 담당자당 근무 상태가 여러 행으로 갈라진다.
	 */
	@Test
	void rejectsSecondPresenceForSameOperator() {
		repository.saveAndFlush(OperatorPresence.connect(30L, NOW));

		assertThatThrownBy(() -> repository.saveAndFlush(OperatorPresence.connect(30L, NOW.plusSeconds(60))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findsOnlyStaleOfflinePresences() {
		OperatorPresence longOffline = OperatorPresence.connect(30L, NOW.minusSeconds(600));
		longOffline.change(OperatorPresenceStatus.OFFLINE, NOW.minusSeconds(600));
		OperatorPresence recentOffline = OperatorPresence.connect(31L, NOW.minusSeconds(10));
		recentOffline.change(OperatorPresenceStatus.OFFLINE, NOW.minusSeconds(10));
		OperatorPresence onDuty = OperatorPresence.connect(32L, NOW.minusSeconds(600));
		repository.saveAllAndFlush(List.of(longOffline, recentOffline, onDuty));

		List<OperatorPresence> stale = repository.findAllByStatusAndLastSeenAtBefore(
				OperatorPresenceStatus.OFFLINE, NOW.minusSeconds(180));
		List<OperatorPresence> notOffline = repository.findAllByStatusNot(OperatorPresenceStatus.OFFLINE);

		assertThat(stale).extracting(OperatorPresence::getOperatorId).containsExactly(30L);
		assertThat(notOffline).extracting(OperatorPresence::getOperatorId).containsExactly(32L);
	}
}
