package com.ojimasam.safy.media.application;

public record IssueImageUploadCommand(
		Long userId,
		String filename,
		String contentType
) {
}
