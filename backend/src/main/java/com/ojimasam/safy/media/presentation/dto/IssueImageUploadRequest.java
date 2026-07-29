package com.ojimasam.safy.media.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueImageUploadRequest(
		@NotBlank @Size(max = 255) String filename,
		@NotBlank String contentType
) {
}
