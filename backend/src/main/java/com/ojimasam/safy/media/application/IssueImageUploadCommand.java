package com.ojimasam.safy.media.application;

public record IssueImageUploadCommand(
		Long userId,
		Long reportId,
		String filename,
		String contentType
) {
}
