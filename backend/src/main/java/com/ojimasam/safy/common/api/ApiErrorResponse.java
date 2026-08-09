package com.ojimasam.safy.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
		boolean success,
		String code,
		String message,
		List<FieldErrorDetail> fieldErrors,
		String path,
		Instant timestamp
) {
	public ApiErrorResponse {
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}

	public static ApiErrorResponse of(String code, String message, String path) {
		return of(code, message, List.of(), path);
	}

	public static ApiErrorResponse of(
			String code,
			String message,
			List<FieldErrorDetail> fieldErrors,
			String path
	) {
		return new ApiErrorResponse(false, code, message, fieldErrors, path, Instant.now());
	}
}
