package com.ojimasam.safy.report.application.dto;

import java.time.Instant;
import java.util.List;

public record ReportCursorResponse(
		List<ReportFeedItemResponse> content,
		Instant nextCursor,
		boolean hasNext
) {
	public ReportCursorResponse {
		content = List.copyOf(content);
	}
}
