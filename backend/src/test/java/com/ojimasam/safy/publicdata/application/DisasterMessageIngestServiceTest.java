package com.ojimasam.safy.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.publicdata.application.dto.CollectionRunPayload;
import com.ojimasam.safy.publicdata.application.dto.DisasterMessageResponse;
import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.type.CollectionJobType;

/**
 * 재난문자는 문제 있는 한 건이 나머지를 막지 않아야 한다.
 * 원본 API 의 조회 창에 같은 메시지가 계속 남아 있어, 배치 단위로 롤백하면
 * 그 메시지가 창에서 빠질 때까지 이후 모든 재난문자가 적재되지 않는다.
 */
class DisasterMessageIngestServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-29T04:10:00Z");

	private final CollectionRunRecorder recorder = mock(CollectionRunRecorder.class);
	private final DisasterMessageWriter writer = mock(DisasterMessageWriter.class);
	private final DisasterMessageIngestService service =
			new DisasterMessageIngestService(recorder, writer, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void keepsIngestingAfterOneItemFails() {
		givenRecordedRun();
		when(writer.write(any(), any(), any())).thenReturn(true);
		when(writer.write(eq(1L), argThatHasExternalId("SN-2"), any()))
				.thenThrow(new IllegalStateException("저장 실패"));

		int saved = service.ingest(response("SN-1", "SN-2", "SN-3"));

		assertThat(saved).isEqualTo(2);
		verify(writer).write(eq(1L), argThatHasExternalId("SN-3"), eq(NOW));
	}

	@Test
	void skipsEverythingWhenRunIsNotRecorded() {
		when(recorder.record(any(), eq(CollectionJobType.DISASTER_MESSAGE))).thenReturn(Optional.empty());

		assertThat(service.ingest(response("SN-1"))).isZero();
		verify(writer, org.mockito.Mockito.never()).write(any(), any(), any());
	}

	@Test
	void countsOnlyNewlySavedItems() {
		givenRecordedRun();
		when(writer.write(any(), any(), any())).thenReturn(false);

		assertThat(service.ingest(response("SN-1", "SN-2"))).isZero();
	}

	private void givenRecordedRun() {
		CollectionRun run = mock(CollectionRun.class);
		when(run.getId()).thenReturn(1L);
		when(recorder.record(any(), eq(CollectionJobType.DISASTER_MESSAGE))).thenReturn(Optional.of(run));
	}

	private DisasterMessageResponse.Item argThatHasExternalId(String externalId) {
		return org.mockito.ArgumentMatchers.argThat(
				item -> item != null && externalId.equals(item.externalId())
		);
	}

	private DisasterMessageResponse response(String... externalIds) {
		return new DisasterMessageResponse(
				new CollectionRunPayload(
						"run-disaster-1",
						"safetydata.go.kr",
						NOW,
						NOW,
						"SUCCESS",
						externalIds.length,
						0,
						null,
						null,
						null
				),
				List.of(externalIds).stream()
						.map(externalId -> new DisasterMessageResponse.Item(
								externalId,
								NOW,
								"인구 밀집으로 통행에 주의하십시오.",
								List.of("서울특별시 강남구"),
								"안전안내",
								"인구밀집"
						))
						.toList()
		);
	}
}
