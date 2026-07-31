package com.ojimasam.safy.report.application.dto;

import java.time.Instant;
import java.util.List;

public record PublicReportCursorResponse(
		List<PublicReportView> content,
		Instant nextCursor,
		boolean hasNext
) {
	public PublicReportCursorResponse {
		content = List.copyOf(content);
	}
}
