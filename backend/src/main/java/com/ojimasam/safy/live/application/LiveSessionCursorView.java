package com.ojimasam.safy.live.application;

import java.time.Instant;
import java.util.List;

public record LiveSessionCursorView(List<OperatorLiveSessionView> content, Instant nextCursor, boolean hasNext) {
	public LiveSessionCursorView {
		content = List.copyOf(content);
	}
}
