package com.ojimasam.safy.media.application;

import java.util.Map;

public record PresignedPostUpload(String uploadUrl, Map<String, String> formFields) {
	public PresignedPostUpload {
		formFields = Map.copyOf(formFields);
	}
}
