package com.ojimasam.safy.common.api;

public record ApiResponse<T>(
		boolean success,
		T data
) {
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data);
	}

	public static ApiResponse<Void> successWithoutData() {
		return new ApiResponse<>(true, null);
	}
}
