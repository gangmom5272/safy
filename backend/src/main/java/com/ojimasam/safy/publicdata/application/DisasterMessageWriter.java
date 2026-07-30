package com.ojimasam.safy.publicdata.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.CollectionRunRepository;
import com.ojimasam.safy.publicdata.domain.DisasterMessage;
import com.ojimasam.safy.publicdata.domain.DisasterMessageRepository;

/**
 * 재난문자 한 건을 자기 트랜잭션에서 저장한다.
 *
 * 왜 한 건씩인가:
 *   재난문자는 원본 API 의 조회 창에 같은 메시지가 계속 남아 있다.
 *   한 배치를 통째로 한 트랜잭션에 넣으면 문제 있는 한 건이 나머지 전부를 롤백시키고,
 *   다음 주기에 같은 응답을 다시 받아 또 실패한다. 그 메시지가 조회 창에서 빠질 때까지
 *   이후 모든 재난문자가 적재되지 않는다.
 *   혼잡도·행사는 페이로드가 주기적으로 바뀌어 자연 복구되므로 배치 트랜잭션을 유지한다.
 */
@Component
public class DisasterMessageWriter {
	private static final Logger log = LoggerFactory.getLogger(DisasterMessageWriter.class);

	private final DisasterMessageRepository disasterMessageRepository;
	private final CollectionRunRepository collectionRunRepository;

	public DisasterMessageWriter(
			DisasterMessageRepository disasterMessageRepository,
			CollectionRunRepository collectionRunRepository
	) {
		this.disasterMessageRepository = disasterMessageRepository;
		this.collectionRunRepository = collectionRunRepository;
	}

	/**
	 * 호출자가 트랜잭션을 열지 않아야 한다. 열어 두면 이 트랜잭션이 그것에 합류해
	 * 한 건의 실패가 다시 배치 전체를 롤백시킨다.
	 */
	@Transactional
	public boolean write(Long collectionRunId, DisasterMessageResponse.Item item, Instant collectedAt) {
		if (item.externalId() == null || item.externalId().isBlank()
				|| item.message() == null || item.message().isBlank()
				|| item.sentAt() == null
				|| item.emergencyLevel() == null || item.emergencyLevel().isBlank()) {
			log.warn("필수 값이 없는 재난문자를 건너뜁니다. externalId={}", item.externalId());
			return false;
		}

		Optional<CollectionRun> run = collectionRunRepository.findById(collectionRunId);
		if (run.isEmpty()) {
			log.warn("수집 실행 이력을 찾을 수 없어 건너뜁니다. collectionRunId={}", collectionRunId);
			return false;
		}

		if (disasterMessageRepository.existsBySourceAndExternalId(run.get().getSource(), item.externalId())) {
			return false;
		}

		disasterMessageRepository.save(
				DisasterMessage.create(
						run.get(),
						item.externalId(),
						item.message(),
						item.regions() == null ? List.of() : item.regions(),
						item.disasterType(),
						item.emergencyLevel(),
						item.sentAt(),
						run.get().getSource(),
						collectedAt
				)
		);
		return true;
	}
}
