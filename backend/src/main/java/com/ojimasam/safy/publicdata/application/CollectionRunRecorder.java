package com.ojimasam.safy.publicdata.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.publicdata.application.dto.CollectionRunPayload;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.CollectionRunRepository;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;
import com.ojimasam.safy.publicdata.domain.type.CollectionStatus;

/**
 * FastAPI 응답에 동봉된 수집 실행 메타데이터를 저장하거나, 이미 적재된 실행이면 그것을 재사용한다.
 * FastAPI 의 수집 주기와 우리 폴링 주기가 어긋나면 같은 실행을 두 번 받게 되므로
 * externalRunId 로 식별하는 이 단계가 없으면 실행 이력이 중복된다.
 */
@Component
public class CollectionRunRecorder {
	private static final Logger log = LoggerFactory.getLogger(CollectionRunRecorder.class);

	private final CollectionRunRepository collectionRunRepository;

	public CollectionRunRecorder(CollectionRunRepository collectionRunRepository) {
		this.collectionRunRepository = collectionRunRepository;
	}

	public Optional<CollectionRun> record(CollectionRunPayload payload, CollectionJobType jobType) {
		// 필수 값이 비면 INSERT 가 실패해 배치 전체가 롤백되므로, 여기서 걸러 다음 주기에 다시 시도한다.
		if (payload == null
				|| payload.externalRunId() == null || payload.externalRunId().isBlank()
				|| payload.source() == null || payload.source().isBlank()
				|| payload.startedAt() == null) {
			log.warn("수집 실행 메타데이터가 불완전해 적재를 건너뜁니다. jobType={}, payload={}", jobType, payload);
			return Optional.empty();
		}

		warnIfNotFullySucceeded(payload, jobType);

		return Optional.of(
				collectionRunRepository.findByExternalRunId(payload.externalRunId())
						.orElseGet(() -> collectionRunRepository.save(toEntity(payload, jobType)))
		);
	}

	/**
	 * FastAPI 는 수집이 실패해도 HTTP 200 에 status=FAILED 와 빈 items 를 담아 응답한다.
	 * 그래서 통신 오류로 잡히지 않고, 적재 건수도 0이라 스케줄러 로그에도 남지 않는다.
	 * 상류 실패가 조용히 지나가지 않도록 여기서 남긴다.
	 */
	private void warnIfNotFullySucceeded(CollectionRunPayload payload, CollectionJobType jobType) {
		Optional<CollectionStatus> status = CollectionStatus.from(payload.status());

		if (status.isEmpty()) {
			log.warn("알 수 없는 수집 상태입니다. 계약이 변경되었는지 확인이 필요합니다. jobType={}, status={}",
					jobType, payload.status());
			return;
		}
		if (status.get() != CollectionStatus.SUCCESS) {
			// errorCode·errorMessage 는 FastAPI 가 타임아웃·예외로 죽었을 때만 채워진다.
			// 개별 항목이 실패했지만 예외 없이 끝난 경우(흔한 경우다)엔 둘 다 null 이고,
			// 실제 실패 사유는 errorDetails(지역별 failures 배열)에만 있다.
			// 이걸 로그에 안 남기면 DB 를 직접 조회해야만 원인을 알 수 있다.
			log.warn("공공데이터 수집이 완전히 성공하지 않았습니다."
							+ " jobType={}, status={}, 성공={}, 실패={}, errorCode={}, errorMessage={}, errorDetails={}",
					jobType, status.get(), payload.successCount(), payload.failureCount(),
					payload.errorCode(), payload.errorMessage(), truncate(payload.errorDetailsAsJson()));
		}
	}

	/** error_details 는 항목당 하나씩 쌓여 실패가 많으면 로그 한 줄이 지나치게 길어질 수 있다. */
	private static String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() > 1000 ? value.substring(0, 1000) + "...(생략)" : value;
	}

	private CollectionRun toEntity(CollectionRunPayload payload, CollectionJobType jobType) {
		return CollectionRun.create(
				payload.externalRunId(),
				jobType,
				payload.source(),
				payload.startedAt(),
				payload.finishedAt(),
				// status 는 계약상 항상 오지만, 알 수 없는 값이면 적재 자체를 막지 않고 성공으로 둔다.
				CollectionStatus.from(payload.status()).orElse(CollectionStatus.SUCCESS),
				payload.successCount(),
				payload.failureCount(),
				payload.errorCode(),
				payload.errorMessage(),
				payload.errorDetailsAsJson()
		);
	}
}
