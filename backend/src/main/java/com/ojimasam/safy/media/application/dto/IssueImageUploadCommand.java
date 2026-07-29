package com.ojimasam.safy.media.application.dto;

public record IssueImageUploadCommand(
		Long userId,
		Long reportId,
		String filename,
		String contentType
) {
}
