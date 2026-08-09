package com.ojimasam.safy.publicdata.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;

/**
 * 긴급 재난 문자를 적재한다. 원본 일련번호(externalId)가 안정적이므로 그것만으로 중복을 판정한다.
 *
 * 이 클래스에는 트랜잭션이 없다. 한 건의 실패가 나머지를 롤백시키지 않도록
 * 저장을 {@link DisasterMessageWriter} 의 개별 트랜잭션에 위임한다.
 */
@Service
public class DisasterMessageIngestService {
	private static final Logger log = LoggerFactory.getLogger(DisasterMessageIngestService.class);

	private final CollectionRunRecorder collectionRunRecorder;
	private final DisasterMessageWriter disasterMessageWriter;
	private final Clock clock;

	@Autowired
	public DisasterMessageIngestService(
			CollectionRunRecorder collectionRunRecorder,
			DisasterMessageWriter disasterMessageWriter
	) {
		this(collectionRunRecorder, disasterMessageWriter, Clock.systemUTC());
	}

	DisasterMessageIngestService(
			CollectionRunRecorder collectionRunRecorder,
			DisasterMessageWriter disasterMessageWriter,
			Clock clock
	) {
		this.collectionRunRecorder = collectionRunRecorder;
		this.disasterMessageWriter = disasterMessageWriter;
		this.clock = clock;
	}

	public int ingest(DisasterMessageResponse response) {
		if (response == null) {
			return 0;
		}
		Optional<CollectionRun> run = collectionRunRecorder.record(
				response.collectionRun(),
				CollectionJobType.DISASTER_MESSAGE
		);
		if (run.isEmpty()) {
			return 0;
		}

		Long runId = run.get().getId();
		Instant collectedAt = clock.instant();
		int saved = 0;
		for (DisasterMessageResponse.Item item : response.safeItems()) {
			try {
				if (disasterMessageWriter.write(runId, item, collectedAt)) {
					saved++;
				}
			} catch (RuntimeException exception) {
				// 이 한 건만 버리고 나머지는 계속 적재한다.
				log.warn("재난문자 1건 적재에 실패해 건너뜁니다. externalId={}", item.externalId(), exception);
			}
		}
		return saved;
	}
}
