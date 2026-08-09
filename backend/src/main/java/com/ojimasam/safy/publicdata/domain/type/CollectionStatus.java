package com.ojimasam.safy.publicdata.domain.type;

import java.util.Optional;

/**
 * FastAPI 가 보내는 수집 실행 상태. 이름을 계약과 정확히 일치시켜야 한다.
 * (FastAPI: schemas/collection.py 의 CollectionStatus Literal)
 */
public enum CollectionStatus {
	SUCCESS,
	PARTIAL_SUCCESS,
	FAILED;

	public static Optional<CollectionStatus> from(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = value.trim();
		for (CollectionStatus status : values()) {
			if (status.name().equalsIgnoreCase(normalized)) {
				return Optional.of(status);
			}
		}
		return Optional.empty();
	}
}
