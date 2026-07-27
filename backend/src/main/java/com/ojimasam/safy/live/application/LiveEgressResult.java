package com.ojimasam.safy.live.application;

import java.util.List;

/**
 * 미디어 서버가 통지한 녹화 작업 결과.
 */
public record LiveEgressResult(String egressId, boolean failed, String error, List<LiveEgressFile> files) {
	public LiveEgressResult {
		files = files == null ? List.of() : List.copyOf(files);
	}

	/** 우리는 참가자당 파일 1개만 요청하므로 첫 번째 결과가 녹화 원본이다. */
	public LiveEgressFile primaryFile() {
		return files.isEmpty() ? null : files.getFirst();
	}

	public record LiveEgressFile(String objectKey, long sizeBytes, Long durationMs) {
	}
}
